package be.nabu.eai.module.services.crud;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;
import be.nabu.eai.module.services.crud.api.CRUDListAction;
import be.nabu.eai.module.web.application.MountableWebFragmentProvider;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;

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
			@Override
			public boolean isBroadcastUpdate() {
				return getConfig().isBroadcastUpdate();
			}
			@Override
			public boolean isBroadcastCreate() {
				return getConfig().isBroadcastCreate();
			}
		};
	}
	
	public void checkBroadcast(ExecutionContext executionContext, String connectionId, String transactionId, ComplexContent content, boolean update) throws ServiceException {
		// we only continue if you have the broadcast library installed
		Artifact resolve = getRepository().resolve("nabu.misc.broadcast.Services.fire");
		if (resolve instanceof DefinedService) {
			DefinedService broadcastService = (DefinedService) resolve;
			
			List<CRUDListAction> actions = new ArrayList<CRUDListAction>();
			// add artifact itself
			actions.add(asListAction());
			// and any additional views
			if (getConfig().getViews() != null) {
				actions.addAll(getConfig().getViews());
			}
			// check for each view whether we want to fire the create/update
			for (CRUDListAction action : actions) {
				// ok, we want to emit it
				if ((update && action.isBroadcastUpdate()) || (!update && action.isBroadcastCreate())) {
					ComplexContent readInput = null;
					// we _really_ don't want to roundtrip to the database unless it is absolutely necessary, so let's see if we can derive the list definition from the create
					// this means all the fields available in the list must also be available in the create
					// if we have imported fields, we are definitely not working on a subset
					if (action.getForeignFields() == null || action.getForeignFields().isEmpty()) {
						String typeId = getId() + ".types.output";
						if (action.getName() != null) {
							typeId += CRUDArtifactManager.getViewName(action.getName());
						}
						DefinedType type = (DefinedType) getRepository().resolve(typeId);
						if (type == null) {
							throw new IllegalStateException("Could not resolve type for broadcasting: " + typeId);
						}
						ComplexContent read = ((ComplexType) type).newInstance();
						boolean isSubset = true;
						ComplexType modifiedType = content.getType();
						for (Element<?> child : TypeUtils.getAllChildren(read.getType())) {
							// if we can't find the child in the original content, alas we'll have to roundtrip
							if (modifiedType.get(child.getName()) == null) {
								isSubset = false;
								break;
							}
							else {
								read.set(child.getName(), content.get(child.getName()));
							}
						}
						if (isSubset) {
							readInput = read;
						}
					}
					if (readInput == null) {
						Element<?> primaryKeyField = CRUDService.getPrimary(content.getType());
						if (primaryKeyField == null) {
							throw new IllegalStateException("Can not broadcast changes because primary key field can not be found");
						}
						Object primaryKey = content.get(primaryKeyField.getName());
						if (primaryKey == null) {
							throw new IllegalStateException("Can not broadcast changes because primary key is null");
						}
						String getServiceId = getId() + ".services.get";
						if (action.getName() != null) {
							getServiceId += CRUDArtifactManager.getViewName(action.getName());
						}
						DefinedService getService = (DefinedService) getRepository().resolve(getServiceId);
						if (getService == null) {
							throw new IllegalStateException("Can not resolve get service: " + getServiceId);
						}
						ServiceRuntime serviceRuntime = new ServiceRuntime(getService, executionContext);
						ComplexContent getServiceInput = getService.getServiceInterface().getInputDefinition().newInstance();
						getServiceInput.set("id", primaryKey);
						getServiceInput.set("connectionId", connectionId);
						getServiceInput.set("transactionId", transactionId);
						ComplexContent getServiceOutput = serviceRuntime.run(getServiceInput);
						readInput = getServiceOutput == null ? null : (ComplexContent) getServiceOutput.get("result");
					}
					if (readInput == null) {
						throw new IllegalStateException("Can not resolve the correct data to broadcast");
					}
					
					ComplexContent broadcastInput = broadcastService.getServiceInterface().getInputDefinition().newInstance();
					broadcastInput.set("data", readInput);
					ServiceRuntime serviceRuntime = new ServiceRuntime(broadcastService, executionContext);
					serviceRuntime.run(broadcastInput);
				}
			}
			
			// we want to look at other CRUDs of the same type and see if they want to fire as well
			for (CRUDArtifact artifact : getRepository().getArtifacts(CRUDArtifact.class)) {
				// not this one again!
				if (getId().equals(artifact.getId())) {
					continue;
				}
				if (getConfig().getCoreType().getId().equals(artifact.getConfig().getCoreType().getId())) {
					artifact.checkBroadcast(executionContext, connectionId, transactionId, content, update);
				}
			}
		}
	}
}
