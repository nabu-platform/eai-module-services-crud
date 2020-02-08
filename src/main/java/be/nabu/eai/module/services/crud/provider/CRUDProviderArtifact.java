package be.nabu.eai.module.services.crud.provider;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.api.ResourceContainer;

public class CRUDProviderArtifact extends JAXBArtifact<CRUDProviderConfiguration> {

	public CRUDProviderArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "crud-provider.xml", CRUDProviderConfiguration.class);
	}

}
