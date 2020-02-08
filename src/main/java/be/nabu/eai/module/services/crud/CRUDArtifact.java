package be.nabu.eai.module.services.crud;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.api.ResourceContainer;

public class CRUDArtifact extends JAXBArtifact<CRUDConfiguration> {

	public CRUDArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "crud.xml", CRUDConfiguration.class);
	}

}
