package be.nabu.eai.module.services.crud;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.module.services.crud.CRUDService.CRUDType;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebApplicationUtils;
import be.nabu.eai.repository.api.LanguageProvider;
import be.nabu.libs.authentication.api.Authenticator;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.PermissionHandler;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.HTTPCodes;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.core.DefaultHTTPResponse;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.http.glue.impl.ResponseMethods;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.binding.api.MarshallableBinding;
import be.nabu.libs.types.binding.api.UnmarshallableBinding;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.MimeUtils;
import be.nabu.utils.mime.impl.PlainMimeContentPart;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

// TODO: fix security context!
public class CRUDListener implements EventHandler<HTTPRequest, HTTPResponse> {
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	private CRUDArtifact artifact;
	private String parentPath;
	private CRUDService service;
	private PathAnalysis pathAnalysis;
	private WebApplication application;
	private Charset charset;

	public CRUDListener(WebApplication application, CRUDArtifact artifact, CRUDService service, String parentPath, String childPath, Charset charset) {
		this.application = application;
		this.artifact = artifact;
		this.service = service;
		this.parentPath = parentPath;
		this.charset = charset;
		if (childPath.startsWith("/")) {
			childPath = childPath.substring(1);
		}
		this.pathAnalysis = GlueListener.analyzePath(childPath);
	}

	@Override
	public HTTPResponse handle(HTTPRequest request) {
		// stop fast if wrong method
		if (!service.getMethod().equalsIgnoreCase(request.getMethod())) {
			return null;
		}
		Token token = null;
		Device device = null;
		try {
			ServiceRuntime.setGlobalContext(new HashMap<String, Object>());
			
			URI uri = HTTPUtils.getURI(request, false);
			String path = URIUtils.normalize(uri.getPath());
			// not in this web artifact
			if (!path.startsWith(parentPath)) {
				return null;
			}
			path = path.substring(parentPath.length());
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
			Map<String, String> analyzed = pathAnalysis.analyze(path);
			// not in this rest path
			if (analyzed == null) {
				return null;
			}
			
			// if we have chosen this rest service, check if the server is offline
			WebApplicationUtils.checkOffline(application, request);
			
			Map<String, List<String>> queryProperties = URIUtils.getQueryProperties(uri);
			
			token = WebApplicationUtils.getToken(application, request);
			device = WebApplicationUtils.getDevice(application, request, token);

			ServiceRuntime.getGlobalContext().put("device", device);
			ServiceRuntime.getGlobalContext().put("service.context", application.getId());
			
			switch (service.getType()) {
				case CREATE: 
					if (artifact.getConfig().getCreateRole() != null) {
						WebApplicationUtils.checkRole(application, token, artifact.getConfig().getCreateRole());
					}
				break;
				case UPDATE: 
					if (artifact.getConfig().getUpdateRole() != null) {
						WebApplicationUtils.checkRole(application, token, artifact.getConfig().getUpdateRole());
					}
				break;
				case GET:
				case LIST: 
					if (artifact.getConfig().getListRole() != null) {
						WebApplicationUtils.checkRole(application, token, artifact.getConfig().getListRole());
					}
				break;
				case DELETE: 
					if (artifact.getConfig().getDeleteRole() != null) {
						WebApplicationUtils.checkRole(application, token, artifact.getConfig().getDeleteRole());
					}
				break;
			}
			
			// maybe at some point we also want to allow for specific rate limit actions/contexts here?
			// for now we are consistent with the default in REST services with no explicit action
			HTTPResponse checkRateLimits = WebApplicationUtils.checkRateLimits(application, token, device, service.getId(), null, request);
			if (checkRateLimits != null) {
				return checkRateLimits;
			}
			
			Header contentTypeHeader = MimeUtils.getHeader("Content-Type", request.getContent().getHeaders());
			String contentType = contentTypeHeader == null ? null : contentTypeHeader.getValue().trim().replaceAll(";.*$", "");

			ComplexContent body = null;
			if (request.getContent() instanceof ContentPart) {
				ReadableContainer<ByteBuffer> readable = ((ContentPart) request.getContent()).getReadable();
				if (readable != null) {
					try {
						UnmarshallableBinding binding;
						if (contentType == null) {
							throw new HTTPException(415, "Unsupported request content type", "Unsupported request content type: " + contentType, token);
						}
						else if (contentType.equalsIgnoreCase("application/xml") || contentType.equalsIgnoreCase("text/xml")) {
							binding = new XMLBinding((ComplexType) service.getInput(), charset);
							((XMLBinding) binding).setIgnoreUndefined(true);
						}
						else if (contentType.equalsIgnoreCase("application/json") || contentType.equalsIgnoreCase("application/javascript")) {
							binding = new JSONBinding((ComplexType) service.getInput(), charset);
							((JSONBinding) binding).setIgnoreUnknownElements(true);
						}
						else {
							throw new HTTPException(415, "Unsupported request content type", "Unsupported request content type: " + contentType, token);
						}
						
						try {
							body = binding.unmarshal(IOUtils.toInputStream(((ContentPart) request.getContent()).getReadable()), new Window[0]);
						}
						catch (IOException e) {
							throw new HTTPException(500, "Unexpected I/O exception", e, token);
						}
						catch (ParseException e) {
							throw new HTTPException(400, "Message can not be parsed", "Message can not be parsed using specification: " + service.getInput(), e, token);
						}
					}
					finally {
						readable.close();
					}
				}
			}
			
			ExecutionContext newExecutionContext = application.getRepository().newExecutionContext(token);
			ComplexContent output = call(newExecutionContext, token, uri, queryProperties, body, WebApplicationUtils.getLanguage(application, request));
			
			switch (service.getType()) {
				case GET:
					output = output == null ? null : (ComplexContent) output.get("result");
				break;
			}
			
			if (output != null) {
				if (artifact.getConfig().isAllowHeaderAsQueryParameter()) {
					WebApplicationUtils.queryToHeader(request, queryProperties);
				}
				
				
				MarshallableBinding binding = RESTUtils.getOutputBinding(request, output.getType(), charset, "application/json", false, false);
				
//				List<String> acceptedContentTypes = request.getContent() != null
//					? MimeUtils.getAcceptedContentTypes(request.getContent().getHeaders())
//					: new ArrayList<String>();
//				acceptedContentTypes.retainAll(ResponseMethods.allowedTypes);
//				contentType = acceptedContentTypes.isEmpty() ? "application/json" : acceptedContentTypes.get(0);
//				if (contentType.equalsIgnoreCase("application/xml")) {
//					binding = new XMLBinding(output.getType(), charset);
//				}
//				else if (contentType.equalsIgnoreCase("application/json")) {
//					binding = new JSONBinding(output.getType(), charset);
//				}
//				else {
//					throw new HTTPException(500, "Unsupported response content type: " + contentType);
//				}
				
				if (binding == null) {
					throw new HTTPException(500, "Unsupported response content types: " + MimeUtils.getAcceptedContentTypes(request.getContent().getHeaders()));
				}
				contentType = RESTUtils.getContentTypeFor(binding);
				
				List<Header> headers = new ArrayList<Header>();
				ByteArrayOutputStream content = new ByteArrayOutputStream();
				binding.marshal(content, (ComplexContent) output);
				byte[] byteArray = content.toByteArray();
				headers.add(new MimeHeader("Content-Length", "" + byteArray.length));
				headers.add(new MimeHeader("Content-Type", contentType + "; charset=" + charset.name()));
				PlainMimeContentPart part = new PlainMimeContentPart(null,
					IOUtils.wrap(byteArray, true),
					headers.toArray(new Header[headers.size()])
				);
				return new DefaultHTTPResponse(request, 200, HTTPCodes.getMessage(200), part);
			}
			else {
				return new DefaultHTTPResponse(request, 200, HTTPCodes.getMessage(200), new PlainMimeEmptyPart(null, 
					new MimeHeader("Content-Length", "0")));
			}
		}
		catch (HTTPException e) {
			if (e.getToken() == null) {
				e.setToken(token);
			}
			if (e.getDevice() == null) {
				e.setDevice(device);
			}
			e.getContext().addAll(Arrays.asList(service.getId()));
			throw e;
		}
		catch (Exception e) {
			HTTPException httpException = new HTTPException(500, "Could not execute service", "Could not execute service: " + service.getId(), e, token);
			httpException.getContext().addAll(Arrays.asList(service.getId()));
			httpException.setDevice(device);
			throw httpException;
		}
		finally {
			ServiceRuntime.setGlobalContext(null);
		}
	}

	public ComplexContent call(ExecutionContext context, Token token, URI uri, Map<String, List<String>> queryProperties, ComplexContent body, String language) throws ServiceException, IOException {
		String path = URIUtils.normalize(uri.getPath());
		// not in this web artifact
		if (!path.startsWith(parentPath)) {
			return null;
		}
		path = path.substring(parentPath.length());
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		Map<String, String> analyzed = pathAnalysis.analyze(path);
		if (analyzed == null) {
			return null;
		}
		return call(context, token, queryProperties, analyzed, body, language);
	}
	
	private ComplexContent call(ExecutionContext executionContext, Token token, Map<String, List<String>> queryProperties, Map<String, String> pathParameters, ComplexContent body, String language) throws ServiceException, IOException {
		ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
		
		// the problem is the context: the id might not be a node, the parent might not be a node, we might not be using nodes, we might not be using cms at all
		// so how do we validate the context? for create & list, we presume the parent field is the context? for update & delete, it is likely the instance itself
		// if there is no context, you _must_ have a global permission which is in a way stricter than a specific context permission, so we should be able to do that at least
		// check permissions
		PermissionHandler permissionHandler = application.getPermissionHandler();
		String action = service.getPermissionAction();
		String context = null;
		String parentQueryName = null;
		if (CRUDType.LIST.equals(service.getType())) {
			if (artifact.getConfig().getFilters() != null) {
				Element<?> securityContext = service.getSecurityContext();
				if (securityContext != null) {
					for (CRUDFilter filter : artifact.getConfig().getFilters()) {
						if (securityContext.getName().equals(filter.getKey())) {
							parentQueryName = filter.getAlias() == null ? filter.getKey() : filter.getAlias();
							break;
						}
					}
				}
			}
			if (parentQueryName != null) {
				context = pathParameters.get("contextId");
			}
		}
		
		if (permissionHandler != null) {
			switch(service.getType()) {
				case CREATE:
					if (pathParameters.get("contextId") != null) {
						context = pathParameters.get("contextId");
					}
				break;
				case GET:
				case UPDATE:
				case DELETE:
					if (artifact.getConfig().getProvider().isPrimaryKeySecurityContext()) {
						context = pathParameters.get("id");
					}
					else if (pathParameters.get("contextId") != null) {
						context = pathParameters.get("contextId");
					}
				break;
				case LIST:
					if (parentQueryName == null && service.hasSecurityContextFilter()) {
						throw new HTTPException(400, "A security context id is required");
					}
				break;
			}
			
			if (action != null && !permissionHandler.hasPermission(token, context, action)) {
				throw new HTTPException(token == null ? 401 : 403, "User does not have permission to execute the rest service", "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have permission to run the CRUD service: " + service.getId(), token);
			}
		}
		
		String chosenLanguage = null;
		// if we indicated that we want to use the language, we injected a query parameter to do so
		if (artifact.getConfig().isUseLanguage()) {
			if (artifact.getConfig().isUseExplicitLanguage() ) {
				List<String> list = queryProperties.get("language");
				if (list != null && !list.isEmpty()) {
					chosenLanguage = list.get(0);
				}
			}
			// if we are doing a select and we did not specify an explicit language, we want to use the language of the user?
			else if (service.getType() == CRUDType.LIST) {
				chosenLanguage = language;
			}
			// check that we choose a valid language
			LanguageProvider languageProvider = application.getLanguageProvider();
			if (languageProvider != null) {
				List<String> supportedLanguages = languageProvider.getSupportedLanguages();
				if (supportedLanguages != null && !supportedLanguages.contains(chosenLanguage)) {
					chosenLanguage = null;
				}
			}
		}
		
		switch (service.getType()) {
			case CREATE:
				input.set("instance", body);
				if (pathParameters.get("contextId") != null) {
					input.set("contextId", pathParameters.get("contextId"));
				}
			break;
			case UPDATE:
				input.set("id", pathParameters.get("id"));
				input.set("instance", body);
				if (artifact.getConfig().isUseLanguage() && chosenLanguage != null) {
					input.set("language", chosenLanguage);
				}
			break;
			case GET:
			case DELETE:
				input.set("id", pathParameters.get("id"));
			break;
			case LIST:
				List<String> limit = queryProperties.get("limit");
				if (limit != null && !limit.isEmpty()) {
					input.set("limit", limit.get(0));
				}
				List<String> offset = queryProperties.get("offset");
				if (offset != null && !offset.isEmpty()) {
					input.set("offset", offset.get(0));
				}
				List<String> orderBy = queryProperties.get("orderBy");
				if (orderBy != null && !orderBy.isEmpty()) {
					input.set("orderBy", orderBy);
				}
				if (artifact.getConfig().isUseLanguage() && chosenLanguage != null) {
					input.set("language", chosenLanguage);
				}
				if (artifact.getConfig().getFilters() != null) {
					for (CRUDFilter filter : artifact.getConfig().getFilters()) {
						if (filter.isInput()) {
							List<String> list = queryProperties.get(filter.getAlias() == null ? filter.getKey() : filter.getAlias());
							if (list != null && !list.isEmpty()) {
								input.set("filter/" + (filter.getAlias() == null ? filter.getKey() : filter.getAlias()), list);
							}
						}
					}
				}
				if (parentQueryName != null) {
					input.set("filter/" + parentQueryName + "[0]", context);
				}
			break;
		}
		
		ServiceRuntime runtime = new ServiceRuntime(service, executionContext);
		// we set the service context to the web application, rest services can be mounted in multiple applications
		ServiceUtils.setServiceContext(runtime, application.getId());
		return runtime.run(input);
	}
}
