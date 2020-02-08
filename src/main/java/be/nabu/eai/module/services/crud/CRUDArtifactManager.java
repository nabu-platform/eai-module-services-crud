package be.nabu.eai.module.services.crud;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class CRUDArtifactManager extends JAXBArtifactManager<CRUDConfiguration, CRUDArtifact> {

	public CRUDArtifactManager() {
		super(CRUDArtifact.class);
	}

	@Override
	protected CRUDArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new CRUDArtifact(id, container, repository);
	}

}
