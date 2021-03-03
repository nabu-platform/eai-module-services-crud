package be.nabu.eai.module.services.crud;

import java.util.List;

import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;
import be.nabu.eai.module.services.crud.CRUDService.CRUDType;
import be.nabu.eai.module.services.crud.api.CRUDListAction;

public class CRUDView implements CRUDListAction {
	private List<String> roles;
	private List<CRUDFilter> filters;
	private List<ForeignNameField> foreignFields;
	private List<String> blacklistFields;
	private String name;
	private CRUDType type;
	public List<String> getRoles() {
		return roles;
	}
	public void setRoles(List<String> roles) {
		this.roles = roles;
	}
	public List<CRUDFilter> getFilters() {
		return filters;
	}
	public void setFilters(List<CRUDFilter> filters) {
		this.filters = filters;
	}
	public List<ForeignNameField> getForeignFields() {
		return foreignFields;
	}
	public void setForeignFields(List<ForeignNameField> foreignFields) {
		this.foreignFields = foreignFields;
	}
	public List<String> getBlacklistFields() {
		return blacklistFields;
	}
	public void setBlacklistFields(List<String> blacklistFields) {
		this.blacklistFields = blacklistFields;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public CRUDType getType() {
		return type;
	}
	public void setType(CRUDType type) {
		this.type = type;
	}
}
