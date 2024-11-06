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
