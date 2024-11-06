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

import java.util.Set;

import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.pojo.POJOInterfaceResolver;
import be.nabu.libs.types.api.ComplexContent;
import java.util.List;

public class CRUDEnrichmentService implements DefinedService {

	private CRUDService listService;
	private String id;

	public CRUDEnrichmentService(String id, CRUDService listService) {
		this.id = id;
		this.listService = listService;
	}
	
	@Override
	public ServiceInterface getServiceInterface() {
		return new POJOInterfaceResolver().resolve("be.nabu.eai.repository.api.ObjectEnricher.apply");
	}

	@Override
	public ServiceInstance newInstance() {
		return new ServiceInstance() {
			@Override
			public Service getDefinition() {
				return CRUDEnrichmentService.this;
			}
			@SuppressWarnings("unchecked")
			@Override
			public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
				listService.apply(
					(String) input.get("typeId"), 
					(String) input.get("language"), 
					(List<Object>) input.get("instances"), 
					(String) input.get("keyField"), 
					(List<String>) input.get("fieldsToEnrich")
				);
				return null;
			}
		};
	}

	@Override
	public Set<String> getReferences() {
		return null;
	}

	@Override
	public String getId() {
		return id;
	}

}
