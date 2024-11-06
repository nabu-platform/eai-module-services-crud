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

import javax.jws.WebParam;
import javax.jws.WebResult;

import be.nabu.eai.module.services.crud.CRUDFilter;
import be.nabu.eai.module.services.crud.CRUDService.TotalCount;
import be.nabu.eai.module.services.crud.ListResult;
import be.nabu.eai.module.services.crud.provider.CRUDMeta;

public interface CRUDProvider {
	
	// id is one of the filters (optionally)
	@WebResult(name = "results")
	public ListResult list(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			// the id of the type we are querying
			@WebParam(name = "typeId") String typeId,
			// the base type id the crud is based on
			@WebParam(name = "coreTypeId") String coreTypeId,
			@WebParam(name = "limit") Integer limit, 
			@WebParam(name = "offset") Long offset,
			@WebParam(name = "totalCount") TotalCount totalCount,
			@WebParam(name = "orderBy") List<String> orderBy,
			@WebParam(name = "statistics") List<String> statistics,
			@WebParam(name = "filters") List<CRUDFilter> filters,
			@WebParam(name = "language") String language,
			// it is unclear if we want generic configuration at this point 
			// and even if we have generic configuration, if this belongs to provider-specific configuration or rather all providers?
			// if it is provider-specific, the REST endpoints won't know what the parameters do, making it hard to force this to true
			// this particular parameter is likely relevant enough to promote to a dedicated parameter rather than a generic one
			@WebParam(name = "limitToUser") Boolean limitToUser,
			// whether it should be attempted to return a lazy version of the resultset (which might not always be possible)
			@WebParam(name = "lazy") Boolean lazy,
			// some generic metadata we have about the crud service
			@WebParam(name = "meta") CRUDMeta meta,
			@WebParam(name = "configuration") Object configuration);
	
	public void create(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "instance") Object object,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "coreTypeId") String coreTypeId,
			@WebParam(name = "meta") CRUDMeta meta,
			@WebParam(name = "configuration") Object configuration);
	
	public void update(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "instance") Object object,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "coreTypeId") String coreTypeId,
			@WebParam(name = "meta") CRUDMeta meta,
			@WebParam(name = "configuration") Object configuration);
	
	public void delete(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "coreTypeId") String coreTypeId,
			@WebParam(name = "id") Object id,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "meta") CRUDMeta meta,
			@WebParam(name = "configuration") Object configuration);

	public default void createBatch(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "instances") List<Object> objects,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "coreTypeId") String coreTypeId,
			@WebParam(name = "meta") CRUDMeta meta,
			@WebParam(name = "configuration") Object configuration) {
		if (objects != null && !objects.isEmpty()) {
			for (Object object : objects) {
				create(connectionId, transactionId, object, language, changeTracker, typeId, coreTypeId, meta, configuration);
			}
		}
	}
	
	public default void updateBatch(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "instances") List<Object> objects,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "coreTypeId") String coreTypeId,
			@WebParam(name = "meta") CRUDMeta meta,
			@WebParam(name = "configuration") Object configuration) {
		if (objects != null && !objects.isEmpty()) {
			for (Object object : objects) {
				update(connectionId, transactionId, object, language, changeTracker, typeId, coreTypeId, meta, configuration);
			}
		}
	}
	
	public default void deleteBatch(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "coreTypeId") String coreTypeId,
			@WebParam(name = "ids") List<Object> ids,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "meta") CRUDMeta meta,
			@WebParam(name = "configuration") Object configuration) {
		if (ids != null && !ids.isEmpty()) {
			for (Object id : ids) {
				delete(connectionId, transactionId, typeId, coreTypeId, id, language, changeTracker, meta, configuration);
			}
		}
	}
}
