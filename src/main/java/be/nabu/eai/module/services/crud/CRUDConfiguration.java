package be.nabu.eai.module.services.crud;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.Comment;
import be.nabu.eai.api.InterfaceFilter;
import be.nabu.eai.module.services.crud.CRUDService.TotalCount;
import be.nabu.eai.module.services.crud.provider.CRUDProviderArtifact;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.artifacts.api.DataSourceProviderArtifact;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.annotation.Field;

@XmlRootElement(name = "crud")
public class CRUDConfiguration {
	
	public static class ForeignNameField {
		private String foreignName;
		private String localName;
		private String foreignKey;
		public String getForeignName() {
			return foreignName;
		}
		public void setForeignName(String foreignName) {
			this.foreignName = foreignName;
		}
		public String getLocalName() {
			return localName;
		}
		public void setLocalName(String localName) {
			this.localName = localName;
		}
		public String getForeignKey() {
			return foreignKey;
		}
		public void setForeignKey(String foreignKey) {
			this.foreignKey = foreignKey;
		}
	}

	// this was always set to true when exposed as rest, but especially for management purposes this might not be useful
	// e.g. in some management setups the users that are used in management are simply not known to the application itself
	private boolean restLimitToUser = true;
	
	private boolean broadcastCreate, broadcastUpdate, hooks;
	
	// when you run a create or update, do you want the created result back or a full get result? depending on the blacklisting and/or importing these can be vastly different
	private boolean useListOutputForCreate, useListOutputForUpdate;
	
	private List<CRUDView> views;

	private boolean allowHeaderAsQueryParameter = true;
	
	// the data type we are wrapping around
	private DefinedType coreType;
	
	private CRUDProviderArtifact provider;
	
	private DefinedService changeTracker;
	
	// the fields we want to blacklist for creation and/or updates
	private List<String> createBlacklistFields, updateBlacklistFields, listBlacklistFields, updateRegenerateFields;
	
	private List<ForeignNameField> foreignFields;
	
	// the field we want to use to check security context
	private String securityContextField;
	
	// you can configure the rest service to use the current service context as the permission context for security checks
	private boolean useServiceContextAsPermissionContext, useWebApplicationAsPermissionContext, useProjectAsPermissionContext, useGlobalPermissionContext;
	
	// allow for custom security context
	// instead of a fully custom context, you can also set a custom prefix, allowing for dynamic lookup
	// IF you have primary key as security, you can set a security context for the id itself
	private String customSecurityContext, securityContextFieldPrefix, primaryKeySecurityContextPrefix;
	
	// we can also set roles
	private List<String> createRole, updateRole, listRole, deleteRole;
	
	private String createPermission, updatePermission, listPermission, getPermission, deletePermission;
	
	private List<CRUDFilter> filters;
	
	private boolean useLanguage;
	
	private boolean useExplicitLanguage;
	
	private DataSourceProviderArtifact connection;
	
	private TotalCount defaultTotalCount;
	
	private Boolean primaryKeySecurityContext;
	
	// the name is necessary for some things like permissions, components...
	// if none is set, we assume the root name of the document
	private String basePath, name;
	
	// you can configure a max limit
	private Integer maxLimit;

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
	@Field(hide = "useServiceContextAsPermissionContext == true || useWebApplicationAsPermissionContext == true || useProjectAsPermissionContext == true || customSecurityContext != null || useGlobalPermissionContext == true")
	public String getSecurityContextField() {
		return securityContextField;
	}
	public void setSecurityContextField(String securityContextField) {
		this.securityContextField = securityContextField;
	}
	@Field(hide = "securityContextField != null || useWebApplicationAsPermissionContext == true || useProjectAsPermissionContext == true || customSecurityContext != null || useGlobalPermissionContext == true || primaryKeySecurityContext == true")
	public boolean isUseServiceContextAsPermissionContext() {
		return useServiceContextAsPermissionContext;
	}
	public void setUseServiceContextAsPermissionContext(boolean useServiceContextAsPermissionContext) {
		this.useServiceContextAsPermissionContext = useServiceContextAsPermissionContext;
	}
	@Field(hide = "useServiceContextAsPermissionContext == true || securityContextField != null || useProjectAsPermissionContext == true || customSecurityContext != null || useGlobalPermissionContext == true || primaryKeySecurityContext == true")
	public boolean isUseWebApplicationAsPermissionContext() {
		return useWebApplicationAsPermissionContext;
	}
	public void setUseWebApplicationAsPermissionContext(boolean useWebApplicationAsPermissionContext) {
		this.useWebApplicationAsPermissionContext = useWebApplicationAsPermissionContext;
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
		if (filters == null) {
			filters = new ArrayList<CRUDFilter>();
		}
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
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@Comment(title = "The data source to use by default for this connection")
	public DataSourceProviderArtifact getConnection() {
		return connection;
	}
	public void setConnection(DataSourceProviderArtifact connection) {
		this.connection = connection;
	}
	
	public List<ForeignNameField> getForeignFields() {
		if (foreignFields == null) {
			foreignFields = new ArrayList<ForeignNameField>();
		}
		return foreignFields;
	}
	public void setForeignFields(List<ForeignNameField> foreignFields) {
		this.foreignFields = foreignFields;
	}

	@Comment(title = "If you want to create a URL to download the data as a specific type (e.g. excel), you can't manipulate the headers directly to indicate content type, language... By enabling this, you can set a select few headers as a query parameter, specifically 'header:Accept', 'header:Accept-Language' and 'header:'Accept-Content-Disposition'")
	public boolean isAllowHeaderAsQueryParameter() {
		return allowHeaderAsQueryParameter;
	}
	public void setAllowHeaderAsQueryParameter(boolean allowHeaderAsQueryParameter) {
		this.allowHeaderAsQueryParameter = allowHeaderAsQueryParameter;
	}
	
	public List<CRUDView> getViews() {
		return views;
	}
	public void setViews(List<CRUDView> views) {
		this.views = views;
	}
	
	public boolean isBroadcastCreate() {
		return broadcastCreate;
	}
	public void setBroadcastCreate(boolean broadcastCreate) {
		this.broadcastCreate = broadcastCreate;
	}
	public boolean isBroadcastUpdate() {
		return broadcastUpdate;
	}
	public void setBroadcastUpdate(boolean broadcastUpdate) {
		this.broadcastUpdate = broadcastUpdate;
	}
	public Integer getMaxLimit() {
		return maxLimit;
	}
	public void setMaxLimit(Integer maxLimit) {
		this.maxLimit = maxLimit;
	}
	public boolean isUseListOutputForCreate() {
		return useListOutputForCreate;
	}
	public void setUseListOutputForCreate(boolean useListOutputForCreate) {
		this.useListOutputForCreate = useListOutputForCreate;
	}
	public boolean isUseListOutputForUpdate() {
		return useListOutputForUpdate;
	}
	public void setUseListOutputForUpdate(boolean useListOutputForUpdate) {
		this.useListOutputForUpdate = useListOutputForUpdate;
	}
	public String getCreatePermission() {
		return createPermission;
	}
	public void setCreatePermission(String createPermission) {
		this.createPermission = createPermission;
	}
	public String getUpdatePermission() {
		return updatePermission;
	}
	public void setUpdatePermission(String updatePermission) {
		this.updatePermission = updatePermission;
	}
	public String getListPermission() {
		return listPermission;
	}
	public void setListPermission(String listPermission) {
		this.listPermission = listPermission;
	}
	public String getGetPermission() {
		return getPermission;
	}
	public void setGetPermission(String getPermission) {
		this.getPermission = getPermission;
	}
	public String getDeletePermission() {
		return deletePermission;
	}
	public void setDeletePermission(String deletePermission) {
		this.deletePermission = deletePermission;
	}
	public TotalCount getDefaultTotalCount() {
		return defaultTotalCount;
	}
	public void setDefaultTotalCount(TotalCount defaultTotalCount) {
		this.defaultTotalCount = defaultTotalCount;
	}
	@Field(hide = "useServiceContextAsPermissionContext == true || securityContextField != null || customSecurityContext != null || useWebApplicationAsPermissionContext == true || useGlobalPermissionContext == true || primaryKeySecurityContext == true")
	public boolean isUseProjectAsPermissionContext() {
		return useProjectAsPermissionContext;
	}
	public void setUseProjectAsPermissionContext(boolean useProjectAsPermissionContext) {
		this.useProjectAsPermissionContext = useProjectAsPermissionContext;
	}
	@Field(hide = "useServiceContextAsPermissionContext == true || securityContextField != null || useProjectAsPermissionContext == true || useWebApplicationAsPermissionContext == true || useGlobalPermissionContext == true || primaryKeySecurityContext == true")
	public String getCustomSecurityContext() {
		return customSecurityContext;
	}
	public void setCustomSecurityContext(String customSecurityContext) {
		this.customSecurityContext = customSecurityContext;
	}
	
	public boolean isRestLimitToUser() {
		return restLimitToUser;
	}
	public void setRestLimitToUser(boolean restLimitToUser) {
		this.restLimitToUser = restLimitToUser;
	}
	
	@Field(hide = "useServiceContextAsPermissionContext == true || securityContextField != null || useProjectAsPermissionContext == true || useWebApplicationAsPermissionContext == true || customSecurityContext != null || primaryKeySecurityContext == true")
	public boolean isUseGlobalPermissionContext() {
		return useGlobalPermissionContext;
	}
	public void setUseGlobalPermissionContext(boolean useGlobalPermissionContext) {
		this.useGlobalPermissionContext = useGlobalPermissionContext;
	}
	
	@Field(show = "securityContextField != null")
	public String getSecurityContextFieldPrefix() {
		return securityContextFieldPrefix;
	}
	public void setSecurityContextFieldPrefix(String securityContextFieldPrefix) {
		this.securityContextFieldPrefix = securityContextFieldPrefix;
	}
	
	@Field(show = "primaryKeySecurityContext == true")
	public String getPrimaryKeySecurityContextPrefix() {
		return primaryKeySecurityContextPrefix;
	}
	public void setPrimaryKeySecurityContextPrefix(String primaryKeySecurityContextPrefix) {
		this.primaryKeySecurityContextPrefix = primaryKeySecurityContextPrefix;
	}
	
	@Field(hide = "useServiceContextAsPermissionContext == true || useProjectAsPermissionContext == true || useWebApplicationAsPermissionContext == true || customSecurityContext != null || useGlobalPermissionContext == true")
	public Boolean getPrimaryKeySecurityContext() {
		return primaryKeySecurityContext;
	}
	public void setPrimaryKeySecurityContext(Boolean primaryKeySecurityContext) {
		this.primaryKeySecurityContext = primaryKeySecurityContext;
	}
	
	public boolean isHooks() {
		return hooks;
	}
	public void setHooks(boolean hooks) {
		this.hooks = hooks;
	}
	
}
