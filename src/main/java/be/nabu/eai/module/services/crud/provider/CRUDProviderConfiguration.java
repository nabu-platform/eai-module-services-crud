package be.nabu.eai.module.services.crud.provider;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.InterfaceFilter;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.services.api.DefinedService;

public class CRUDProviderConfiguration {
	private DefinedService listService, createService, updateService, deleteService;

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
	
}
