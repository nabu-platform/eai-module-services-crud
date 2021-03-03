package be.nabu.eai.module.services.crud.api;

import java.util.List;

import be.nabu.eai.module.services.crud.CRUDFilter;
import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;

public interface CRUDAction {
	public List<CRUDFilter> getFilters();
	public void setFilters(List<CRUDFilter> filters);
	public List<ForeignNameField> getForeignFields();
	public void setForeignFields(List<ForeignNameField> foreignFields);
	public void setName(String name);
	public String getName();
}
