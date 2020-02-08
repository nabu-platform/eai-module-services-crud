package be.nabu.eai.module.services.crud.provider;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class CRUDProviderManager extends JAXBArtifactManager<CRUDProviderConfiguration, CRUDProviderArtifact> {

	public CRUDProviderManager() {
		super(CRUDProviderArtifact.class);
	}

	@Override
	protected CRUDProviderArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new CRUDProviderArtifact(id, container, repository);
	}

}
