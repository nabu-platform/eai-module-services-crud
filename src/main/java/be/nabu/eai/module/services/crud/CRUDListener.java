package be.nabu.eai.module.services.crud;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebApplicationUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.impl.MimeUtils;

// TODO: fix security context!
public class CRUDListener implements EventHandler<HTTPRequest, HTTPResponse> {
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	private CRUDArtifact artifact;
	private String childPath;
	private String parentPath;
	private CRUDService service;
	private PathAnalysis pathAnalysis;
	private WebApplication application;

	public CRUDListener(WebApplication application, CRUDArtifact artifact, CRUDService service, String parentPath, String childPath) {
		this.application = application;
		this.artifact = artifact;
		this.service = service;
		this.parentPath = parentPath;
		this.childPath = childPath;
		this.pathAnalysis = GlueListener.analyzePath(parentPath + "/" + childPath);
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
			
			Map<String, List<String>> queryProperties = URIUtils.getQueryProperties(uri);
			
			// do a referer check, we only allow cookies to be used if the referer matches the virtual host, because we are dealing with rest services there is no "initial page" scenario to keep track off
			Header refererHeader = MimeUtils.getHeader("Referer", request.getContent().getHeaders());
			URI referer = refererHeader == null ? null : new URI(URIUtils.encodeURI(refererHeader.getValue()));
			boolean refererMatch = WebApplicationUtils.refererMatches(application, referer);
			
			Map<String, List<String>> cookies = refererMatch || EAIResourceRepository.isDevelopment() ? HTTPUtils.getCookies(request.getContent().getHeaders()) : new HashMap<String, List<String>>();
			token = WebApplicationUtils.getToken(application, request);
			device = WebApplicationUtils.getDevice(application, request, token);

			ServiceRuntime.getGlobalContext().put("device", device);
			
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
			
			HTTPResponse checkRateLimits = WebApplicationUtils.checkRateLimits(application, token, device, service.getPermissionAction(), null, request);
			if (checkRateLimits != null) {
				return checkRateLimits;
			}
			
			ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
			
			ExecutionContext newExecutionContext = application.getRepository().newExecutionContext(token);
			ServiceRuntime runtime = new ServiceRuntime(service, newExecutionContext);
			// we set the service context to the web application, rest services can be mounted in multiple applications
			ServiceUtils.setServiceContext(runtime, application.getId());
			ComplexContent output = runtime.run(input);
			
			return null;
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

}
