/*
* Copyright (C) 2020 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
	private boolean broadcastUpdate, broadcastCreate;
	private Integer maxLimit;
	
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
	@Override
	public boolean isBroadcastUpdate() {
		return broadcastUpdate;
	}
	public void setBroadcastUpdate(boolean broadcastUpdate) {
		this.broadcastUpdate = broadcastUpdate;
	}
	@Override
	public boolean isBroadcastCreate() {
		return broadcastCreate;
	}
	public void setBroadcastCreate(boolean broadcastCreate) {
		this.broadcastCreate = broadcastCreate;
	}
	public Integer getMaxLimit() {
		return maxLimit;
	}
	public void setMaxLimit(Integer maxLimit) {
		this.maxLimit = maxLimit;
	}
}
