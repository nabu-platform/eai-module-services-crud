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

package be.nabu.eai.module.services.crud.provider;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.InterfaceFilter;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.types.api.DefinedType;

@XmlRootElement(name = "crudProvider")
public class CRUDProviderConfiguration {
	private DefinedService listService, createService, updateService, deleteService, createBatchService, updateBatchService, deleteBatchService;

	// a base type, it is currently not enforced but it allows you to indicate which base type you expect to be present through extension
	private DefinedType baseType, configurationType;
	
	// you can blacklist fields at the provider level, this is mostly useful when you are working with a base type that has some fields that you want to automanage in the provider
	private List<String> blacklistedFields;
	
	private Boolean primaryKeySecurityContext;
	
	// Whether or not you support statistics
	private boolean supportsStatistics;

	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.services.crud.api.CRUDProvider.list")
	public DefinedService getListService() {
		return listService;
	}
	public void setListService(DefinedService listService) {
		this.listService = listService;
	}

	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.services.crud.api.CRUDProvider.create")
	public DefinedService getCreateService() {
		return createService;
	}
	public void setCreateService(DefinedService createService) {
		this.createService = createService;
	}

	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.services.crud.api.CRUDProvider.update")
	public DefinedService getUpdateService() {
		return updateService;
	}
	public void setUpdateService(DefinedService updateService) {
		this.updateService = updateService;
	}

	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.services.crud.api.CRUDProvider.delete")
	public DefinedService getDeleteService() {
		return deleteService;
	}
	public void setDeleteService(DefinedService deleteService) {
		this.deleteService = deleteService;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.services.crud.api.CRUDProvider.createBatch")
	public DefinedService getCreateBatchService() {
		return createBatchService;
	}
	public void setCreateBatchService(DefinedService createBatchService) {
		this.createBatchService = createBatchService;
	}
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.services.crud.api.CRUDProvider.updateBatch")
	public DefinedService getUpdateBatchService() {
		return updateBatchService;
	}
	public void setUpdateBatchService(DefinedService updateBatchService) {
		this.updateBatchService = updateBatchService;
	}
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.eai.module.services.crud.api.CRUDProvider.deleteBatch")
	public DefinedService getDeleteBatchService() {
		return deleteBatchService;
	}
	public void setDeleteBatchService(DefinedService deleteBatchService) {
		this.deleteBatchService = deleteBatchService;
	}
	
	public List<String> getBlacklistedFields() {
		return blacklistedFields;
	}
	public void setBlacklistedFields(List<String> blacklistedFields) {
		this.blacklistedFields = blacklistedFields;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getBaseType() {
		return baseType;
	}
	public void setBaseType(DefinedType baseType) {
		this.baseType = baseType;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getConfigurationType() {
		return configurationType;
	}
	public void setConfigurationType(DefinedType configurationType) {
		this.configurationType = configurationType;
	}
	
	public Boolean getPrimaryKeySecurityContext() {
		return primaryKeySecurityContext;
	}
	public void setPrimaryKeySecurityContext(Boolean primaryKeySecurityContext) {
		this.primaryKeySecurityContext = primaryKeySecurityContext;
	}
	public boolean isSupportsStatistics() {
		return supportsStatistics;
	}
	public void setSupportsStatistics(boolean supportsStatistics) {
		this.supportsStatistics = supportsStatistics;
	}

}
