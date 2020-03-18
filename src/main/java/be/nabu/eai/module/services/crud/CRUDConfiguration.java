package be.nabu.eai.module.services.crud;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.Comment;
import be.nabu.eai.api.InterfaceFilter;
import be.nabu.eai.module.services.crud.provider.CRUDProviderArtifact;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.types.api.DefinedType;

@XmlRootElement(name = "crud")
public class CRUDConfiguration {
	// the data type we are wrapping around
	private DefinedType coreType;
	
	private CRUDProviderArtifact provider;
	
	private DefinedService changeTracker;
	
	// the fields we want to blacklist for creation and/or updates
	private List<String> createBlacklistFields, updateBlacklistFields, listBlacklistFields, updateRegenerateFields;
	
	// the field we want to use to check security context
	private String securityContextField;
	
	// we can also set roles
	private List<String> createRole, updateRole, listRole, deleteRole;
	
	private List<CRUDFilter> filters;
	
	private boolean useLanguage;
	
	private boolean useExplicitLanguage;
	
	// the name is necessary for some things like permissions, components...
	// if none is set, we assume the root name of the document
	private String basePath, name;

	@InterfaceFilter(implement = "be.nabu.libs.services.jdbc.api.ChangeTracker.track")
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedService getChangeTracker() {
		return changeTracker;
	}
	public void setChangeTracker(DefinedService changeTracker) {
		this.changeTracker = changeTracker;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public CRUDProviderArtifact getProvider() {
		return provider;
	}
	public void setProvider(CRUDProviderArtifact provider) {
		this.provider = provider;
	}
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getCoreType() {
		return coreType;
	}
	public void setCoreType(DefinedType coreType) {
		this.coreType = coreType;
	}

	public List<String> getCreateBlacklistFields() {
		return createBlacklistFields;
	}
	public void setCreateBlacklistFields(List<String> createFields) {
		this.createBlacklistFields = createFields;
	}

	public List<String> getUpdateBlacklistFields() {
		return updateBlacklistFields;
	}
	public void setUpdateBlacklistFields(List<String> updateFields) {
		this.updateBlacklistFields = updateFields;
	}

	public String getSecurityContextField() {
		return securityContextField;
	}
	public void setSecurityContextField(String securityContextField) {
		this.securityContextField = securityContextField;
	}
	public List<String> getCreateRole() {
		return createRole;
	}
	public void setCreateRole(List<String> createRole) {
		this.createRole = createRole;
	}
	public List<String> getUpdateRole() {
		return updateRole;
	}
	public void setUpdateRole(List<String> updateRole) {
		this.updateRole = updateRole;
	}
	public List<String> getListRole() {
		return listRole;
	}
	public void setListRole(List<String> listRole) {
		this.listRole = listRole;
	}
	public List<String> getDeleteRole() {
		return deleteRole;
	}
	public void setDeleteRole(List<String> deleteRole) {
		this.deleteRole = deleteRole;
	}
	public String getBasePath() {
		return basePath;
	}
	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}
	public List<String> getListBlacklistFields() {
		return listBlacklistFields;
	}
	public void setListBlacklistFields(List<String> listFields) {
		this.listBlacklistFields = listFields;
	}
	public List<CRUDFilter> getFilters() {
		return filters;
	}
	public void setFilters(List<CRUDFilter> filters) {
		this.filters = filters;
	}
	public List<String> getUpdateRegenerateFields() {
		return updateRegenerateFields;
	}
	public void setUpdateRegenerateFields(List<String> updateRegenerateFields) {
		this.updateRegenerateFields = updateRegenerateFields;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	@Comment(title = "Whether or not we want to use language features at all, if you turn this on, we expose language input parameters for the services and at the REST level, we use the implicit user language for listing")
	public boolean isUseLanguage() {
		return useLanguage;
	}
	public void setUseLanguage(boolean useLanguage) {
		this.useLanguage = useLanguage;
	}
	
	@Comment(title = "If you enabled language features but want to use explicit language selection at the REST level. We no longer use the implicit language selection for the user.")
	public boolean isUseExplicitLanguage() {
		return useExplicitLanguage;
	}
	public void setUseExplicitLanguage(boolean useExplicitLanguage) {
		this.useExplicitLanguage = useExplicitLanguage;
	}
	
}
