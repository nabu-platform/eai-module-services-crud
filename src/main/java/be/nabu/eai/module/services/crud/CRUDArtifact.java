package be.nabu.eai.module.services.crud;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.web.application.MountableWebFragmentProvider;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;

public class CRUDArtifact extends JAXBArtifact<CRUDConfiguration> implements MountableWebFragmentProvider {

	public CRUDArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "crud.xml", CRUDConfiguration.class);
	}

	@Override
	public List<WebFragment> getWebFragments() {
		List<WebFragment> fragments = new ArrayList<WebFragment>();
		for (DefinedService service : getRepository().getArtifacts(DefinedService.class)) {
			if (service.getId().startsWith(getId() + ".") && service instanceof WebFragment) {
				fragments.add((WebFragment) service);
			}
		}
		return fragments;
	}

	@Override
	public String getRelativePath() {
		return "/";
	}

}
