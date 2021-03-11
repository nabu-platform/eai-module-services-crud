package be.nabu.eai.module.services.crud;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;
import be.nabu.eai.module.services.crud.api.CRUDListAction;
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

	public CRUDListAction asListAction() {
		return new CRUDListAction() {
			@Override
			public void setRoles(List<String> roles) {
				getConfig().setListRole(roles);
			}
			@Override
			public void setForeignFields(List<ForeignNameField> foreignFields) {
				getConfig().setForeignFields(foreignFields);
			}
			@Override
			public void setFilters(List<CRUDFilter> filters) {
				getConfig().setFilters(filters);
			}
			@Override
			public void setBlacklistFields(List<String> blacklistFields) {
				getConfig().setListBlacklistFields(blacklistFields);
			}
			@Override
			public List<String> getRoles() {
				return getConfig().getListRole();
			}
			@Override
			public List<ForeignNameField> getForeignFields() {
				return getConfig().getForeignFields();
			}
			@Override
			public List<CRUDFilter> getFilters() {
				return getConfig().getFilters();
			}
			@Override
			public List<String> getBlacklistFields() {
				return getConfig().getListBlacklistFields();
			}
			@Override
			public void setName(String name) {
				// do nothing
			}
			@Override
			public String getName() {
				return null;
			}
		};
	}
}
