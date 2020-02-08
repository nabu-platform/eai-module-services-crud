package be.nabu.eai.module.services.crud;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.module.services.crud.provider.CRUDProviderArtifact;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.types.api.DefinedType;

@XmlRootElement(name = "crud")
public class CRUDConfiguration {
	// the data type we are wrapping around
	private DefinedType coreType;
	
	private CRUDProviderArtifact provider;
	
	// the fields we want to expose for creation and/or updates
	private List<String> createFields, updateFields, listFields;
	
	// the field we want to use to check security context
	private String securityContextField, parentField;
	
	// we can also set roles
	private String createRole, updateRole, listRole, deleteRole;
	
	private List<CRUDFilter> filters;
	
	private String basePath;

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

	public List<String> getCreateFields() {
		return createFields;
	}
	public void setCreateFields(List<String> createFields) {
		this.createFields = createFields;
	}

	public List<String> getUpdateFields() {
		return updateFields;
	}
	public void setUpdateFields(List<String> updateFields) {
		this.updateFields = updateFields;
	}

	public String getSecurityContextField() {
		return securityContextField;
	}
	public void setSecurityContextField(String securityContextField) {
		this.securityContextField = securityContextField;
	}

	public String getCreateRole() {
		return createRole;
	}
	public void setCreateRole(String createRole) {
		this.createRole = createRole;
	}

	public String getUpdateRole() {
		return updateRole;
	}
	public void setUpdateRole(String updateRole) {
		this.updateRole = updateRole;
	}

	public String getListRole() {
		return listRole;
	}
	public void setListRole(String listRole) {
		this.listRole = listRole;
	}

	public String getDeleteRole() {
		return deleteRole;
	}
	public void setDeleteRole(String deleteRole) {
		this.deleteRole = deleteRole;
	}
	public String getBasePath() {
		return basePath;
	}
	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}
	public String getParentField() {
		return parentField;
	}
	public void setParentField(String parentField) {
		this.parentField = parentField;
	}
	public List<String> getListFields() {
		return listFields;
	}
	public void setListFields(List<String> listFields) {
		this.listFields = listFields;
	}
	public List<CRUDFilter> getFilters() {
		return filters;
	}
	public void setFilters(List<CRUDFilter> filters) {
		this.filters = filters;
	}
	
}
