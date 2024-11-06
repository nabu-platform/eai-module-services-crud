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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.eai.module.services.crud.CRUDService.CRUDType;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.ObjectEnricher;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.mask.MaskedContent;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;

public class CRUDBatchService implements DefinedService, ObjectEnricher {

	private CRUDArtifact artifact;
	private String id;
	private CRUDType type;
	private DefinedStructure updateInput;
	private DefinedStructure updateOutput;
	private Structure input, output;
	private DefinedStructure updateIntermediaryInput;

	public CRUDBatchService(CRUDArtifact artifact, String id, CRUDType type, DefinedStructure updateInput, DefinedStructure updateIntermediaryInput, DefinedStructure updateOutput) {
		this.artifact = artifact;
		this.id = id;
		this.type = type;
		this.updateInput = updateInput;
		this.updateIntermediaryInput = updateIntermediaryInput;
		this.updateOutput = updateOutput;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Structure getInput() {
		if (input == null) {
			synchronized(this) {
				if (input == null) {
					Structure input = new Structure();
					input.setName("input");
					Element<?> primary = CRUDService.getPrimary((ComplexType) artifact.getConfig().getCoreType());
					input.add(new SimpleElementImpl<String>("connectionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<String>("transactionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					switch(type) {
						case UPDATE:
							// we always expose language at this level (?)
							input.add(new SimpleElementImpl<String>("language", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
							input.add(new ComplexElementImpl("instance", updateInput, input, 
								new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
								new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
						break;
						case DELETE:
							input.add(new SimpleElementImpl("id", (SimpleType<?>) primary.getType(), input,
								new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
								new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
						break;
					}
					this.input = input;
				}
			}
		}
		return input;
	}
	
	private Structure getOutput() {
		if (output == null) {
			synchronized(this) {
				if (output == null) {
					Structure output = new Structure();
					output.setName("output");
					switch(type) {
						case UPDATE:
							output.add(new ComplexElementImpl("updated", updateOutput, output, 
								new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
								new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
						break;
					}
					this.output = output;
				}
			}
		}
		return output;
	}
	
	@Override
	public ServiceInterface getServiceInterface() {
		return new ServiceInterface() {
			@Override
			public ServiceInterface getParent() {
				return null;
			}
			@Override
			public ComplexType getOutputDefinition() {
				return getOutput();
			}
			@Override
			public ComplexType getInputDefinition() {
				return getInput();
			}
		};
	}

	@Override
	public ServiceInstance newInstance() {
		return new ServiceInstance() {
			@Override
			public Service getDefinition() {
				return CRUDBatchService.this;
			}
			@Override
			public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
				Service service = null;
				switch(type) {
//					case CREATE: service = artifact.getConfig().getProvider().getConfig().getCreateService(); break;
					case UPDATE: service = artifact.getConfig().getProvider().getConfig().getUpdateService(); break;
					case DELETE: service = artifact.getConfig().getProvider().getConfig().getDeleteService(); break;
//					default: service = artifact.getConfig().getProvider().getConfig().getListService(); 
				}
				String connectionId = input == null ? null : (String) input.get("connectionId");
				String transactionId = input == null ? null : (String) input.get("transactionId");
				String language = input == null ? null : (String) input.get("language");
				
				// if we have configured a connection id, use that
				if (connectionId == null && artifact.getConfig().getConnection() != null) {
					connectionId = artifact.getConfig().getConnection().getId();
				}
				
				ComplexContent output = getServiceInterface().getOutputDefinition().newInstance();
				ComplexContent serviceInput = null;
				switch(type) {
					case UPDATE:
						Object list = input == null ? null : input.get("instance");
						if (list != null) {
							CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(list.getClass());
							if (handler == null) {
								throw new IllegalArgumentException("Invalid list object");
							}
							Element<?> primary = CRUDService.getPrimary(updateIntermediaryInput);
							if (primary == null) {
								throw new IllegalStateException("Could not find primary key field definition for updating");
							}
							int counter = 0;
							List<Object> updatedInstances = new ArrayList<Object>();
							// for now we just loop, in the future we can update providers to allow for smart batch updating etc
							for (Object object : handler.getAsIterable(list)) {
								if (object != null) {
									if (!(object instanceof ComplexContent)) {
										object = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(object);
									}
									if (object == null) {
										throw new IllegalArgumentException("Object can not be cast to complex content");
									}
									// we want to map the primary key and optionally add some other things
									MaskedContent updateInstance = new MaskedContent((ComplexContent) object, updateIntermediaryInput);
									if (artifact.getConfig().getUpdateRegenerateFields() != null) {
										for (String name : artifact.getConfig().getUpdateRegenerateFields()) {
											Element<?> element = updateInstance.getType().get(name);
											if (element != null && Date.class.isAssignableFrom(((SimpleType<?>) element.getType()).getInstanceClass())) {
												updateInstance.set(name, new Date());
											}
										}
									}
									updatedInstances.add(updateInstance);
									
									serviceInput = artifact.getConfig().getProvider().getConfig().getUpdateService().getServiceInterface().getInputDefinition().newInstance();
									// because we use restrictions where we synchronize the collection name etc, the update statement that is generated in the end should only update the fields you selected
									// as such we don't need to do anything
									serviceInput.set("instance", updateInstance);
									serviceInput.set("connectionId", connectionId);
									serviceInput.set("transactionId", transactionId);
									serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
									serviceInput.set("language", language);
									serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
									output.set("updated[" + counter++ + "]", new MaskedContent(updateInstance, updateOutput));
									
									ServiceRuntime runtime = new ServiceRuntime(service, executionContext);
									runtime.run(serviceInput);
								}
							}
							if (!updatedInstances.isEmpty()) {
								EAIRepositoryUtils.persist(updatedInstances, language, executionContext);
							}
						}
						
					break;
					case DELETE:
						Object deleteId = input == null ? null : input.get("id");
						if (deleteId != null) {
							CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(deleteId.getClass());
							if (handler == null) {
								throw new IllegalArgumentException("Invalid list object");
							}
							for (Object singleId : handler.getAsIterable(deleteId)) {
								deleteId(executionContext, connectionId, transactionId, singleId);
							}
						}
						// TODO: no enrichment delete yet...
					break;
				}
				return output;
			}
		};
	}
	private void deleteId(ExecutionContext executionContext, String connectionId, String transactionId, Object singleId) throws ServiceException {
		ComplexContent serviceInput = artifact.getConfig().getProvider().getConfig().getDeleteService().getServiceInterface().getInputDefinition().newInstance();
		serviceInput.set("id", singleId);
		serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
		serviceInput.set("connectionId", connectionId);
		serviceInput.set("transactionId", transactionId);
//								serviceInput.set("language", language);
		serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
		ServiceRuntime runtime = new ServiceRuntime(artifact.getConfig().getProvider().getConfig().getDeleteService(), executionContext);
		runtime.run(serviceInput);
	}
	
	private void createSingle(ExecutionContext executionContext, String connectionId, String transactionId, String language, ComplexContent single) throws ServiceException {
		ComplexContent serviceInput = artifact.getConfig().getProvider().getConfig().getCreateService().getServiceInterface().getInputDefinition().newInstance();
		serviceInput.set("instance", single);
		serviceInput.set("connectionId", connectionId);
		serviceInput.set("transactionId", transactionId);
		serviceInput.set("language", language);
		serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
		serviceInput.set("coreTypeId", artifact.getConfig().getCoreType().getId());
		serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
		ServiceRuntime runtime = new ServiceRuntime(artifact.getConfig().getProvider().getConfig().getCreateService(), executionContext);
		runtime.run(serviceInput);
	}
	private void updateSingle(ExecutionContext executionContext, String connectionId, String transactionId, String language, ComplexContent single) throws ServiceException {
		ComplexContent serviceInput = artifact.getConfig().getProvider().getConfig().getUpdateService().getServiceInterface().getInputDefinition().newInstance();
		// because we use restrictions where we synchronize the collection name etc, the update statement that is generated in the end should only update the fields you selected
		// as such we don't need to do anything
		serviceInput.set("instance", single);
		serviceInput.set("connectionId", connectionId);
		serviceInput.set("transactionId", transactionId);
		serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
		serviceInput.set("language", language);
		serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
		ServiceRuntime runtime = new ServiceRuntime(artifact.getConfig().getProvider().getConfig().getUpdateService(), executionContext);
		runtime.run(serviceInput);
	}

	@Override
	public Set<String> getReferences() {
		return null;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void apply(String typeId, String language, List<Object> instances, String keyField, List<String> fieldsToEnrich) throws ServiceException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void persist(String typeId, String language, List<Object> instances, String keyField, List<String> fieldsToPersist) throws ServiceException {
		if (type != CRUDType.UPDATE) {
			throw new UnsupportedOperationException();	
		}
		if (keyField == null) {
			throw new IllegalArgumentException("Need a key field, not provided for: " + typeId);
		}
		if (fieldsToPersist == null) {
			throw new IllegalArgumentException("No persisted fields found for: " + typeId);
		}
		if (instances.size() > 0) {
			// we get the list service, it should provide use with an object enrichment target to fetch the current instances (to detect a delta)
			CRUDService service = (CRUDService) EAIResourceRepository.getInstance().resolve(artifact.getId() + ".services.list");
			List<ComplexContent> contents = new ArrayList<ComplexContent>();
			Type targetType = null;
			for (Object single : instances) {
				if (single == null) {
					continue;
				}
				if (!(single instanceof ComplexContent)) {
					single = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(single);
					if (single == null) {
						throw new IllegalArgumentException("Could not cast to complex content");
					}
				}
				if (targetType == null) {
					targetType = ((ComplexContent) single).getType();
				}
				contents.add((ComplexContent) single);
			}
			Element<?> foreignKeyField = service.getForeignKeyField(targetType);
			
			List<Object> ids = new ArrayList<Object>();
			for (ComplexContent content : contents) {
				ids.add(content.get(keyField));
			}
			
			Map<Object, List<Object>> asIs = service.getRecordMap(language, foreignKeyField, ids);
			List<Object> toDelete = new ArrayList<Object>();
			List<Object> toUpdate = new ArrayList<Object>();
			List<Object> toCreate = new ArrayList<Object>();
			for (ComplexContent content : contents) {
				Object primaryKey = content.get(keyField);
				if (asIs.containsKey(primaryKey)) {
					for (String field : fieldsToPersist) {
						Element<?> fieldElement = content.getType().get(field);
						boolean isList = fieldElement.getType().isList(fieldElement.getProperties());
						List<Object> currentList = asIs.get(primaryKey);
						Object toBe = content.get(field);
						if (isList) {
							List<Object> toBeList = (List<Object>) toBe;
							if (toBeList == null || toBeList.isEmpty()) {
								if (currentList != null && !currentList.isEmpty()) {
									toDelete.addAll(currentList);
								}
							}
							else if (currentList == null || currentList.isEmpty()) {
								toCreate.addAll(toBeList);
							}
							else {
								// TODO: get ids from one and the other, compare to see if we need to update, delete or create
								Map<Object, Object> currentIds = new HashMap<Object, Object>();
								for (Object single : currentList) {
									currentIds.put(service.getPrimaryKey((ComplexContent) single), single);
								}
								for (Object single : toBeList) {
									Object singleKey = service.getPrimaryKey((ComplexContent) single);
									if (currentIds.containsKey(singleKey)) {
										toUpdate.add(single);
										currentIds.remove(singleKey);
									}
									else {
										toCreate.add(single);
									}
								}
								toDelete.addAll(currentIds.values());
							}
						}
						else {
							Object current = currentList == null || currentList.isEmpty() ? null : currentList.get(0); 
							if (toBe == null) {
								if (current != null) {
									toDelete.add(current);
								}
							}
							else if (current == null) {
								if (toBe != null) {
									toCreate.add(current);
								}
							}
							else {
								// TODO: check that anything is actually changed before updating (?)
								Object currentPrimaryKey = service.getPrimaryKey((ComplexContent) current);
								Object toBePrimaryKey = service.getPrimaryKey((ComplexContent) toBe);
								if (toBePrimaryKey.equals(currentPrimaryKey)) {
									toUpdate.add(current);
								}
								else {
									toDelete.add(current);
									toCreate.add(toBe);
								}
							}
						}
					}
				}
			}
			for (Object single : toDelete) {
				deleteId(ServiceRuntime.getRuntime().getExecutionContext(), null, null, service.getPrimaryKey((ComplexContent) single));
			}
			if (!toUpdate.isEmpty()) {
				for (Object single : toUpdate) {
					updateSingle(ServiceRuntime.getRuntime().getExecutionContext(), null, null, language, (ComplexContent) single);
				}
			}
			if (!toCreate.isEmpty()) {
				for (Object single : toCreate) {
					createSingle(ServiceRuntime.getRuntime().getExecutionContext(), null, null, language, (ComplexContent) single);
				}
			}
		}
	}

}
