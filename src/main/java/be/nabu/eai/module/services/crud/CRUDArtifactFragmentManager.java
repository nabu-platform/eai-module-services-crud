package be.nabu.eai.module.services.crud;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import be.nabu.eai.module.services.crud.provider.CRUDProviderArtifact;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.CreatableArtifactFragmentManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.impl.BaseNodeMetadataArtifactFragmentManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.DataSourceProviderArtifact;
import be.nabu.libs.services.DefinedServiceInterfaceResolverFactory;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.pojo.POJOUtils;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;

public class CRUDArtifactFragmentManager extends BaseNodeMetadataArtifactFragmentManager<CRUDArtifact> implements CreatableArtifactFragmentManager<CRUDArtifact> {

	private static final String CRUD_PATH = "crud.xml";
	private static final String ARTIFACT_RESOURCE_PATH = "crud.xml";
	private static final String CONTENT_TYPE = "application/xml";
	private static final String ARTIFACT_TYPE = "crud";
	private static final String ARTIFACT_CATEGORY = "artifact";
	private static final String GUIDELINES_PATH = "/guidelines/crud.md";
	private static final String KNOWN_CRUD_PROVIDERS_PLACEHOLDER = "{{KNOWN_CRUD_PROVIDERS}}";
	private static final String CHANGE_TRACKER_INTERFACE = "be.nabu.libs.services.jdbc.api.ChangeTracker.track";
	private static final String PERMISSION_CONTEXT_TYPE = "permissionContextType";
	private static final String BROADCAST_UPDATE = "broadcastUpdate";
	private static final String BROADCAST_CREATE = "broadcastCreate";
	private static final String HOOKS = "hooks";

	@Override
	public Entry createArtifact(Entry parent, String name) {
		try {
			RepositoryEntry entry = ((RepositoryEntry) parent).createNode(name, new CRUDArtifactManager(), true);
			CRUDArtifact artifact = new CRUDArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
			new CRUDArtifactManager().save(entry, artifact);
			return entry;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<ArtifactFragment> listFragments(final CRUDArtifact artifact) {
		List<ArtifactFragment> fragments = new ArrayList<ArtifactFragment>(getSharedFragments(artifact));
		fragments.add(new ArtifactFragment() {
			@Override
			public boolean isEditable() {
				return EAIResourceRepository.getInstance().getEntry(artifact.getId()) instanceof ResourceEntry;
			}

			@Override
			public boolean isRemovable() {
				return false;
			}

			@Override
			public String getPath() {
				return CRUD_PATH;
			}

			@Override
			public String getContent() {
				try {
					return marshalFragment(artifact);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public String getContentType() {
				return CONTENT_TYPE;
			}

			@Override
			public String getArtifactId() {
				return artifact.getId();
			}

			@Override
			public String getFragmentType() {
				return ARTIFACT_TYPE;
			}

			@Override
			public Map<String, String> getProperties() {
				return new LinkedHashMap<String, String>();
			}

			@Override
			public Long getLastModified() {
				return getFragmentLastModified(artifact.getId(), ARTIFACT_RESOURCE_PATH);
			}
		});
		return fragments;
	}

	@Override
	public List<Validation<?>> updateFragment(CRUDArtifact artifact, String path, String oldContent, String newContent) {
		if (!CRUD_PATH.equals(path)) {
			if ("metadata.xml".equals(path)) {
				return super.updateFragment(artifact, path, oldContent, newContent);
			}
			throw new UnsupportedOperationException("Updating fragments is only supported for metadata.xml and crud.xml on CRUD artifacts");
		}
		ResourceEntry entry = (ResourceEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		try {
			CRUDArtifact candidate = new CRUDArtifact(artifact.getId(), entry.getContainer(), entry.getRepository());
			Document currentDocument = parseDocument(marshalRawFragment(artifact));
			Document document = parseDocument(newContent);
			retainField(currentDocument.getDocumentElement(), document.getDocumentElement(), document, BROADCAST_UPDATE);
			retainField(currentDocument.getDocumentElement(), document.getDocumentElement(), document, BROADCAST_CREATE);
			retainField(currentDocument.getDocumentElement(), document.getDocumentElement(), document, HOOKS);
			String unmarshalledContent = removePermissionContextType(document);
			candidate.setConfig(candidate.unmarshal(new ByteArrayInputStream(unmarshalledContent.getBytes(StandardCharsets.UTF_8))));
			applyPermissionContextType(document, candidate.getConfig(), validations);
			validateConfiguration(candidate.getConfig(), validations);
			if (!hasErrors(validations)) {
				validations.addAll(new CRUDArtifactManager().save(entry, candidate));
				if (!hasErrors(validations)) {
					artifact.setConfig(candidate.getConfig());
				}
			}
		}
		catch (Exception e) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
		}
		return validations;
	}

	@Override
	public String getGuidelines(List<String> fragmentTypes) {
		List<String> sections = new ArrayList<String>();
		String guidelines = EAIRepositoryUtils.loadCachedClasspathResource(CRUDArtifactFragmentManager.class, GUIDELINES_PATH);
		if (guidelines != null && !guidelines.trim().isEmpty()) {
			guidelines = guidelines.replace(KNOWN_CRUD_PROVIDERS_PLACEHOLDER, buildDynamicCrudProviderList());
			sections.add(guidelines.trim());
		}
		String metadataGuidance = super.getGuidelines(fragmentTypes);
		if (metadataGuidance != null && !metadataGuidance.trim().isEmpty()) {
			sections.add(metadataGuidance.trim());
		}
		return String.join("\n\n", sections).trim();
	}

	@Override
	public Class<CRUDArtifact> getArtifactClass() {
		return CRUDArtifact.class;
	}

	@Override
	public String getArtifactType() {
		return ARTIFACT_TYPE;
	}

	@Override
	public String getArtifactCategory() {
		return ARTIFACT_CATEGORY;
	}

	private void validateConfiguration(CRUDConfiguration configuration, List<Validation<?>> validations) {
		validateConnection(configuration.getConnection(), validations);
		validateChangeTracker(configuration.getChangeTracker(), validations);
		validateProvider(configuration.getProvider(), validations);
	}

	private void validateConnection(DataSourceProviderArtifact connection, List<Validation<?>> validations) {
		if (connection == null) {
			return;
		}
		if (!(connection instanceof DataSourceProviderArtifact)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Configured connection '" + connection.getId() + "' is not a " + DataSourceProviderArtifact.class.getName()));
		}
	}

	private void validateChangeTracker(DefinedService changeTracker, List<Validation<?>> validations) {
		validateConfiguredService(changeTracker, "changeTracker", CHANGE_TRACKER_INTERFACE, validations);
	}

	private void validateProvider(CRUDProviderArtifact provider, List<Validation<?>> validations) {
		if (provider == null) {
			return;
		}
		if (!(provider instanceof CRUDProviderArtifact)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Configured provider '" + provider.getId() + "' is not a " + CRUDProviderArtifact.class.getName()));
		}
	}

	private void validateConfiguredService(DefinedService service, String fieldName, String interfaceName, List<Validation<?>> validations) {
		if (service == null) {
			return;
		}
		DefinedServiceInterface iface = DefinedServiceInterfaceResolverFactory.getInstance().getResolver().resolve(interfaceName);
		if (iface == null) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Unknown interface requested for '" + fieldName + "': " + interfaceName));
		}
		else if (!POJOUtils.isImplementation(service, iface)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Configured service '" + service.getId() + "' for '" + fieldName + "' does not implement " + interfaceName));
		}
	}

	private boolean hasErrors(List<Validation<?>> validations) {
		for (Validation<?> validation : validations) {
			if (validation != null && validation.getSeverity() == ValidationMessage.Severity.ERROR) {
				return true;
			}
		}
		return false;
	}

	private String buildDynamicCrudProviderList() {
		List<String> providerIds = new ArrayList<String>();
		for (CRUDProviderArtifact artifact : EAIResourceRepository.getInstance().getArtifacts(CRUDProviderArtifact.class)) {
			if (artifact != null && artifact.getId() != null && !artifact.getId().trim().isEmpty()) {
				providerIds.add(artifact.getId().trim());
			}
		}
		if (providerIds.isEmpty()) {
			return "none discovered in the current repository";
		}
		Collections.sort(providerIds);
		return String.join(", ", providerIds);
	}

	private String marshalFragment(CRUDArtifact artifact) throws Exception {
		Document document = parseDocument(marshalRawFragment(artifact));
		appendPermissionContextType(document, document.getDocumentElement(), artifact.getConfig());
		removeDirectChild(document.getDocumentElement(), BROADCAST_UPDATE);
		removeDirectChild(document.getDocumentElement(), BROADCAST_CREATE);
		removeDirectChild(document.getDocumentElement(), HOOKS);
		return toXml(document);
	}

	private String marshalRawFragment(CRUDArtifact artifact) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		artifact.marshal(artifact.getConfig(), output);
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}

	private void appendPermissionContextType(Document document, Element root, CRUDConfiguration config) {
		String value = getPermissionContextType(config);
		if (value == null) {
			removeDirectChild(root, PERMISSION_CONTEXT_TYPE);
			return;
		}
		Element existing = getDirectChild(root, PERMISSION_CONTEXT_TYPE);
		if (existing == null) {
			existing = document.createElement(PERMISSION_CONTEXT_TYPE);
			root.appendChild(existing);
		}
		existing.setTextContent(value);
	}

	private void applyPermissionContextType(Document document, CRUDConfiguration config, List<Validation<?>> validations) {
		Element element = getDirectChild(document.getDocumentElement(), PERMISSION_CONTEXT_TYPE);
		if (element == null) {
			return;
		}
		String value = element.getTextContent() == null ? null : element.getTextContent().trim();
		config.setUseServiceContextAsPermissionContext(false);
		config.setUseWebApplicationAsPermissionContext(false);
		config.setUseProjectAsPermissionContext(false);
		config.setUseGlobalPermissionContext(false);
		config.setUseAsAuthorizationServiceContext(false);
		if (value == null || value.isEmpty()) {
			return;
		}
		if ("SERVICE_CONTEXT".equals(value)) {
			config.setUseServiceContextAsPermissionContext(true);
		}
		else if ("WEB_APPLICATION".equals(value)) {
			config.setUseWebApplicationAsPermissionContext(true);
		}
		else if ("PROJECT".equals(value)) {
			config.setUseProjectAsPermissionContext(true);
		}
		else if ("GLOBAL".equals(value)) {
			config.setUseGlobalPermissionContext(true);
		}
		else if ("AUTHORIZATION_SERVICE_CONTEXT".equals(value)) {
			config.setUseAsAuthorizationServiceContext(true);
		}
		else {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Unsupported permissionContextType: " + value));
		}
	}

	private String getPermissionContextType(CRUDConfiguration config) {
		if (config.getSecurityContextField() != null && !config.getSecurityContextField().trim().isEmpty()) {
			return null;
		}
		if (config.getCustomSecurityContext() != null && !config.getCustomSecurityContext().trim().isEmpty()) {
			return null;
		}
		if (config.getPrimaryKeySecurityContext() != null && config.getPrimaryKeySecurityContext()) {
			return null;
		}
		if (config.isUseServiceContextAsPermissionContext()) {
			return "SERVICE_CONTEXT";
		}
		if (config.isUseWebApplicationAsPermissionContext()) {
			return "WEB_APPLICATION";
		}
		if (config.isUseProjectAsPermissionContext()) {
			return "PROJECT";
		}
		if (config.isUseGlobalPermissionContext()) {
			return "GLOBAL";
		}
		if (config.isUseAsAuthorizationServiceContext()) {
			return "AUTHORIZATION_SERVICE_CONTEXT";
		}
		return null;
	}

	private String removePermissionContextType(Document document) throws Exception {
		removeDirectChild(document.getDocumentElement(), PERMISSION_CONTEXT_TYPE);
		return toXml(document);
	}

	private Document parseDocument(String content) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
	}

	private Element getDirectChild(Element parent, String name) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child instanceof Element) {
				Element element = (Element) child;
				String childName = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
				if (name.equals(childName)) {
					return element;
				}
			}
		}
		return null;
	}

	private void retainField(Element currentRoot, Element updatedRoot, Document updated, String name) {
		if (getDirectChild(updatedRoot, name) == null) {
			Element existing = getDirectChild(currentRoot, name);
			if (existing != null) {
				updatedRoot.appendChild(updated.importNode(existing, true));
			}
		}
	}

	private void removeDirectChild(Element parent, String name) {
		Element child = getDirectChild(parent, name);
		while (child != null) {
			parent.removeChild(child);
			child = getDirectChild(parent, name);
		}
	}

	private String toXml(Document document) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		EAIRepositoryUtils.prettyPrint(document, output);
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}
}
