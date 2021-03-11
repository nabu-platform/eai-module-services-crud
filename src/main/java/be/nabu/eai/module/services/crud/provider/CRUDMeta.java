package be.nabu.eai.module.services.crud.provider;

public class CRUDMeta {
	private String permissionAction;
	private String securityField;

	public String getPermissionAction() {
		return permissionAction;
	}

	public void setPermissionAction(String permissionAction) {
		this.permissionAction = permissionAction;
	}

	public String getSecurityField() {
		return securityField;
	}

	public void setSecurityField(String securityField) {
		this.securityField = securityField;
	}
}
