package be.nabu.eai.module.services.crud.api;

import java.util.List;

public interface CRUDListAction extends CRUDAction {
	public List<String> getRoles();
	public void setRoles(List<String> roles);
	public List<String> getBlacklistFields();
	public void setBlacklistFields(List<String> blacklistFields);
	public boolean isBroadcastUpdate();
	public boolean isBroadcastCreate();
}
