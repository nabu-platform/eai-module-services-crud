package be.nabu.eai.module.services.crud;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.Key;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.client.impl.protocol.task.GetPartitionsMessageTask;

import be.nabu.eai.module.keystore.KeyStoreArtifact;
import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.module.services.crud.CRUDService.CRUDType;
import be.nabu.eai.module.services.crud.api.CRUDListAction;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebApplicationUtils;
import be.nabu.eai.repository.api.LanguageProvider;
import be.nabu.eai.repository.util.Filter;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.authentication.api.Authenticator;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.PermissionHandler;
import be.nabu.libs.authentication.api.PotentialPermissionHandler;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.HTTPCodes;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.core.DefaultHTTPResponse;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.http.jwt.JWTBody;
import be.nabu.libs.http.jwt.JWTUtils;
import be.nabu.libs.http.jwt.enums.JWTAlgorithm;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.KeyValuePair;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.binding.api.MarshallableBinding;
import be.nabu.libs.types.binding.api.UnmarshallableBinding;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.types.utils.KeyValuePairImpl;
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
	
	private CRUDArtifact artifact;
	private String parentPath;
	private CRUDService service;
	private PathAnalysis pathAnalysis;
	private WebApplication application;
	private Charset charset;
	private Logger logger = LoggerFactory.getLogger(getClass());

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
			List<Header> headers = new ArrayList<Header>();
			
			ComplexContent output = call(newExecutionContext, token, uri, queryProperties, body, WebApplicationUtils.getLanguage(application, request), headers);
			
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
				
				ByteArrayOutputStream content = new ByteArrayOutputStream();
				binding.marshal(content, (ComplexContent) output);
				byte[] byteArray = content.toByteArray();
				headers.add(new MimeHeader("Content-Length", "" + byteArray.length));
				headers.add(new MimeHeader("Content-Type", contentType + "; charset=" + charset.name()));
				
				Map<String, String> values = MimeUtils.getHeaderAsValues("Accept-Content-Disposition", request.getContent().getHeaders());
				// we are asking for an attachment download
				if (values.get("value") != null && values.get("value").equalsIgnoreCase("attachment")) {
					String fileName = values.get("filename");
					if (fileName != null) {
						fileName = fileName.replaceAll("[^\\w.-]+", "");
					}
					else {
						fileName = "unnamed";
					}
					headers.add(new MimeHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\""));
				}
				
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

	public ComplexContent call(ExecutionContext context, Token token, URI uri, Map<String, List<String>> queryProperties, ComplexContent body, String language, List<Header> responseHeaders) throws ServiceException, IOException {
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
		return call(context, token, queryProperties, analyzed, body, language, responseHeaders);
	}
	
	private ComplexContent call(ExecutionContext executionContext, Token token, Map<String, List<String>> queryProperties, Map<String, String> pathParameters, ComplexContent body, String language, List<Header> responseHeaders) throws ServiceException, IOException {
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
			if (service.getFilters() != null) {
				Element<?> securityContext = service.getSecurityContext();
				if (securityContext != null) {
					for (CRUDFilter filter : service.getFilters()) {
						if (filter != null && filter.getKey() != null) {
							if (securityContext.getName().equals(filter.getKey())) {
								parentQueryName = filter.getAlias() == null ? filter.getKey() : filter.getAlias();
								break;
							}
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
				boolean allowed = false;
				// if you specifically did not select a security field, we can check the potential permissions as well
				if (artifact.getConfig().getSecurityContextField() == null) {
					PotentialPermissionHandler potentialPermissionHandler = application.getPotentialPermissionHandler();
					if (potentialPermissionHandler != null) {
						allowed = potentialPermissionHandler.hasPotentialPermission(token, action);
					}
				}
				if (!allowed) {
					throw new HTTPException(token == null ? 401 : 403, "User does not have permission to execute the rest service", "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have permission to run the CRUD service: " + service.getId(), token);
				}
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
				// limit to the user if we have a permission handler
				// if we don't have one configured, it is not enforced on the other actions either and it could backfire trying to force it here
				input.set("limitToUser", permissionHandler != null);
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
				if (service.getFilters() != null) {
					for (CRUDFilter filter : service.getFilters()) {
						if (filter != null && filter.isInput() && filter.getKey() != null) {
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
		runtime.getContext().put("webApplicationId", application.getId());
		ComplexContent output = runtime.run(input);
		
		// INSPIRED BY selectFiltered in jdbc
		// if we have a list action, check if we want to be able to subscribe to events
		// if you do a GET, you are requesting a single specific instance, there is no need/use to subscribe to CREATE events then
		// though you might be interested in UPDATE events, but only if the thing you are looking for actually exists, so if the output is null for a GET, this is irrelevant
		// for a LIST we can do both create and update events
		// we basically want to inject JWT tokens into the headers that you can use to subscribe for more data
		// the create will be based on the filters you pass in (which security-wise you have been cleared for already and business wise you are interested in)
		// the update will be based on the specific primary keys you get back
		// _if_ you register both create and update listeners, the intermediate logic can amend the list of ids in the update listener to include those that are added via create
		CRUDListAction crudListAction = service.getCRUDListAction();
		if (crudListAction != null && (crudListAction.isBroadcastCreate() || crudListAction.isBroadcastUpdate())) {
			KeyStoreArtifact jwtKeyStore = application.getConfig().getJwtKeyStore();
			String jwtKeyAlias = application.getConfig().getJwtKeyAlias();
			// if you don't configure them, we can't send out jwt tokens
			// we don't want to fail at this point, it might be on purpose or legacy
			if (jwtKeyStore != null && jwtKeyAlias != null) {
				String typeId = artifact.getId() + ".types.output";
				if (crudListAction.getName() != null) {
					typeId += CRUDArtifactManager.getViewName(crudListAction.getName());
				}
				DefinedType type = (DefinedType) artifact.getRepository().resolve(typeId);
				String updateId = crudListAction.isBroadcastUpdate() ? UUID.randomUUID().toString().replace("-", "") : null;
				if (type == null) {
					logger.warn("Could not resolve type for streaming: " + typeId);
				}
				else {
					// create broadcasts are only relevant for list services
					// update broadcasts are relevant for list services and for a GET only if it actually has a result
					if ((crudListAction.isBroadcastCreate() && service.getType().equals(CRUDType.LIST)) || (crudListAction.isBroadcastUpdate() && (service.getType().equals(CRUDType.LIST) || (output != null && output.get("result") != null)))) {
						// the glue query
						String query = "";
						String javascript = "";
						if (crudListAction.getFilters() != null && !crudListAction.getFilters().isEmpty()) {
							List<Filter> filters = new ArrayList<Filter>();
							CRUDService.transformFilters(crudListAction.getFilters(), input, filters, false);
							boolean openOr = false;
							// the query or can be different, based on vary filters
							boolean openQueryOr = false;
							for (int i = 0; i < filters.size(); i++) {
								Filter filter = filters.get(i);
								if (filter.getKey() == null) {
									continue;
								}
								if (skipFilter(filter)) {
									continue;
								}
								
								boolean vary = filter instanceof CRUDFilter && ((CRUDFilter) filter).isVary();
								
								if (!query.isEmpty() && !vary) {
									if (filter.isOr()) {
										query += " ||";
									}
									else {
										query += " &&";
									}
								}
								
								if (!javascript.isEmpty()) {
									if (filter.isOr()) {
										javascript += " ||";
									}
									else {
										javascript += " &&";
									}
								}
								
								// start the or
								if (i < filters.size() - 1 && !openOr && filters.get(i + 1).isOr()) {
									if (!vary) {
										query += " (";
										openQueryOr = true;
									}
									javascript += " (";
									openOr = true;
								}
								
								boolean inverse = inverseFilter(filter);
								String operator = filter.getOperator();
								
								boolean isMultipleValues = filter.getValues() != null && filter.getValues().size() >= 2 && CRUDService.inputOperators.contains(operator);
								boolean isSingleValue = filter.getValues() != null && filter.getValues().size() == 1 && CRUDService.inputOperators.contains(operator);
								
								if (inverse && operator.toLowerCase().equals("is null")) {
									operator = "!= null";
									inverse = false;
								}
								else if (inverse && operator.toLowerCase().equals("is not null")) {
									operator = "== null";
									inverse = false;
								}
								else if (inverse) {
									if (!vary) {
										query += " !(";
									}
									javascript += " !(";
								}
								
								if (operator.equals("=")) {
									operator = isMultipleValues ? "?" : "==";
								}
								else if (operator.equals("<>")) {
									operator = isMultipleValues ? "!?" : "!=";
								}
								else if (operator.equals("is null")) {
									operator = "== null";
								}
								else if (operator.equals("is not null")) {
									operator = "!= null";
								}
								else if (operator.equals("like")) {
									operator = "~";
								}
								
								if (!vary) {
									query += " " + filter.getKey();
									query += " " + operator;
								}

								// for multiple values we do another thing in javascript
								// [1,2].indexOf(value) >= 0
								if (!isMultipleValues) {
									javascript += " " + filter.getKey();
								}
								
								// the regex operator does not have an equivalent
								// we are not actually regexing, but a contains
								if (operator.equals("~")) {
									if (filter.isCaseInsensitive()) {
										javascript += ".toLowerCase()";
									}
									javascript += ".indexOf(\"";
								}
								else if (!isMultipleValues) {
									javascript += " " + operator;
								}
								
								if (isMultipleValues || isSingleValue) {
									if (!isMultipleValues) {
										Object value = filter.getValues().get(0);
										if (operator.equals("~")) {
											// escape the double quotes
											String stringValue = ConverterFactory.getInstance().getConverter().convert(value, String.class);
											String regex = value == null ? ".*" : ".*" + Pattern.quote(stringValue.replace("\"", "\\\"")) + ".*";
											// always dotall
											regex = "(?s)" + regex;
											if (filter.isCaseInsensitive()) {
												regex = "(?i)" + regex;
												// for javascript, we embed the string, let's lowercase it here already
												stringValue = stringValue.toLowerCase();
											}
											if (!vary) {
												query += " \"" + regex + "\"";
											}
											
											javascript += stringValue.replace("\"", "\\\"") + "\") >= 0";
										}
										else if (value instanceof String) {
											if (!vary) {
												query += " \"" + ((String) value).replace("\"", "\\\"") + "\"";
											}
											javascript += " \"" + ((String) value).replace("\"", "\\\"") + "\"";
										}
										else {
											if (!vary) {
												query += " " + value;
											}
											javascript += " " + value;
										}
									}
									// only in and not in
									else {
										String objectList = "";
										for (int j = 0; j < filter.getValues().size(); j++) {
											Object value = filter.getValues().get(j);
											if (j > 0) {
												objectList += ", ";
											}
											if (value instanceof String) {
												objectList += " \"" + ((String) value).replace("\"", "\\\"") + "\"";
											}
											else {
												objectList += " " + value;
											}
										}
										if (!vary) {
											query += " series(";
											query += objectList;
											query += ")";
										}
										javascript += " [" + objectList + "].indexOf(" + filter.getKey() + ") " + (operator.equals("?") ? " >= " : " < ") + "0";
									}
								}
								// close the not statement
								if (inverse) {
									if (!vary) {
										query += ")";
									}
									javascript += ")";
								}
								// check if we want to close an or
								if (i < filters.size() - 1 && openOr && !filters.get(i + 1).isOr()) {
									if (!vary && openQueryOr) {
										query += ")";
									}
									javascript += ")";
									openOr = false;
									openQueryOr = false;
								}
							}
							if (openQueryOr) {
								query += ")";
								openQueryOr = false;
							}
							if (openOr) {
								javascript += ")";
								openOr = false;
							}
						}
						JWTBody jwt = new JWTBody();
						// anonymous data streams are probably not a good idea...
						jwt.setAud(token == null ? null : token.getAuthenticationId());
						jwt.setSub(typeId);
						// the jwt has a limited use window
						// the stream has to be started within that window, afterwards a new token must be requested
						// the stream will continue however once set up, even past the expiration
						long time = new Date().getTime();
						// seconds
						// saving space...
	//					jwt.setIat(time / 1000);
						// available for 5 minutes
						time += 1000l * 60 * 5;
						// expressed in seconds
						jwt.setExp(time / 1000);
						// we use the id to reference the subscription initially
						jwt.setJti(UUID.randomUUID().toString().replace("-", ""));
						List<KeyValuePair> pairs = new ArrayList<KeyValuePair>();
						Element<?> primary = CRUDService.getPrimary((ComplexType) type);
						if (primary != null) {
							pairs.add(new KeyValuePairImpl("p", primary.getName()));
						}
						if (query != null && !query.isEmpty()) {
							pairs.add(new KeyValuePairImpl("q", query));
						}
						if (javascript != null && !javascript.isEmpty()) {
							pairs.add(new KeyValuePairImpl("j", javascript));
						}
						// no longer needed
//						if (updateId != null) {
//							pairs.add(new KeyValuePairImpl("u", updateId));
//						}
						if (!pairs.isEmpty()) {
							jwt.setValues(pairs);
						}
						Key key;
						try {
							key = jwtKeyStore.getKeyStore().getPrivateKey(jwtKeyAlias);
						}
						catch (Exception e) {
							try {
								key = jwtKeyStore.getKeyStore().getSecretKey(jwtKeyAlias);
							}
							catch (Exception f) {
								throw new RuntimeException("Could not resolve jwt key alias: " + jwtKeyAlias, f);
							}
						}
						if (key == null) {
							throw new IllegalArgumentException("Can not resolve key '" + jwtKeyAlias + "' in jwt keystore");
						}
						
						// instead of opting for the most secure, we balance overall jwt token size with security
						// if you want the more secure algorithms, set it explicitly
						JWTAlgorithm algorithm = key instanceof SecretKey ? JWTAlgorithm.HS256 : JWTAlgorithm.RS256;
						String encode = JWTUtils.encode(key, jwt, algorithm);
						responseHeaders.add(new MimeHeader("Stream-Token", encode));
					}
					
					
					// it only applies to list and get
					// but for get it only applies if there is actual content
//					if (crudListAction.isBroadcastUpdate() && (service.getType().equals(CRUDType.LIST) || (output != null && output.get("result") != null))) {
//						// TODO: add primary key field to both create token and update token
//						Element<?> primary = CRUDService.getPrimary((ComplexType) type);
//						if (primary != null) {
//							// we capture all the ids involved (or just the one in case of get)
//							// we use a list either way
//							List<Object> ids = new ArrayList<Object>();
//							if (output != null) {
//								if (service.getType().equals(CRUDType.LIST)) {
//									Object object = output.get("results");
//									if (object != null) {
//										for (Object single : (Iterable<?>) object) {
//											if (single == null) {
//												continue;
//											}
//											else if (!(single instanceof ComplexContent)) {
//												single = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(single);
//											}
//											if (single != null) {
//												Object id = ((ComplexContent) single).get(primary.getName());
//												if (id != null) {
//													ids.add(id);
//												}
//											}
//										}
//									}
//								}
//								else {
//									Object object = output.get("result");
//									if (object != null) {
//										if (!(object instanceof ComplexContent)) {
//											object = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(object);
//										}
//										if (object != null) {
//											Object id = ((ComplexContent) object).get(primary.getName());
//											if (id != null) {
//												ids.add(id);
//											}
//										}
//									}
//								}
//							}
//							String query = "";
//							// an id is considered to be numeric or uuid/string/...
//							// all the latter ones are embedded as strings
//							for (Object id : ids) {
//								if (!query.isEmpty()) {
//									query += ", ";
//								}
//								if (!(id instanceof Number)) {
//									query += " \"" + ConverterFactory.getInstance().getConverter().convert(id, String.class).replace("\"", "\\\"") + "\"";
//								}
//								else {
//									query += id;
//								}
//							}
//							query = primary.getName() + " ? series(" + query + ")";
//							
//							JWTBody jwt = new JWTBody();
//							// anonymous data streams are probably not a good idea...
//							jwt.setAud(token == null ? null : token.getAuthenticationId());
//							jwt.setSub(typeId);
//							long time = new Date().getTime();
//							time += 1000l * 60 * 5;
//							jwt.setExp(time / 1000);
//							jwt.setJti(updateId);
//							jwt.setValues(Arrays.asList(new KeyValuePairImpl("q", query), new KeyValuePairImpl("p", primary.getName())));
//							Key key;
//							try {
//								key = jwtKeyStore.getKeyStore().getPrivateKey(jwtKeyAlias);
//							}
//							catch (Exception e) {
//								try {
//									key = jwtKeyStore.getKeyStore().getSecretKey(jwtKeyAlias);
//								}
//								catch (Exception f) {
//									throw new RuntimeException("Could not resolve jwt key alias: " + jwtKeyAlias, f);
//								}
//							}
//							if (key == null) {
//								throw new IllegalArgumentException("Can not resolve key '" + jwtKeyAlias + "' in jwt keystore");
//							}
//							
//							// instead of opting for the most secure, we balance overall jwt token size with security
//							// if you want the more secure algorithms, set it explicitly
//							JWTAlgorithm algorithm = key instanceof SecretKey ? JWTAlgorithm.HS256 : JWTAlgorithm.RS256;
//							String encode = JWTUtils.encode(key, jwt, algorithm);
//							responseHeaders.add(new MimeHeader("Stream-Update-Token", encode));
//						}
//						else {
//							logger.warn("Can not create an update stream if no primary key is found");
//						}
//					}
				}
			}
		}
		
		return output;
	}
	
	private static boolean skipFilter(Filter filter) {
		// if it is not a traditional comparison operator, we assume it is a boolean one
		if (!CRUDService.inputOperators.contains(filter.getOperator()) && filter.getValues() != null && !filter.getValues().isEmpty()) {
			Object object = filter.getValues().get(0);
			if (object == null) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean inverseFilter(Filter filter) {
		if (!CRUDService.inputOperators.contains(filter.getOperator()) && filter.getValues() != null && !filter.getValues().isEmpty()) {
			Object object = filter.getValues().get(0);
			if (object instanceof Boolean && !(Boolean) object) {
				return true;
			}
		}
		return false;
	}
}
