package be.nabu.eai.module.services.crud;

import java.util.Date;
import java.util.Set;

import be.nabu.eai.module.services.crud.CRUDService.CRUDType;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.mask.MaskedContent;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;

public class CRUDBatchService implements DefinedService {

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
					switch(type) {
						case UPDATE:
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
									
									serviceInput = artifact.getConfig().getProvider().getConfig().getUpdateService().getServiceInterface().getInputDefinition().newInstance();
									// because we use restrictions where we synchronize the collection name etc, the update statement that is generated in the end should only update the fields you selected
									// as such we don't need to do anything
									serviceInput.set("instance", updateInstance);
									serviceInput.set("connectionId", connectionId);
									serviceInput.set("transactionId", transactionId);
									serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
									if (artifact.getConfig().isUseLanguage()) {
										serviceInput.set("language", language);
									}
									serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
									output.set("updated[" + counter++ + "]", new MaskedContent(updateInstance, updateOutput));
									
									ServiceRuntime runtime = new ServiceRuntime(service, executionContext);
									runtime.run(serviceInput);
								}
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
							for (Object object : handler.getAsIterable(deleteId)) {
								serviceInput = artifact.getConfig().getProvider().getConfig().getDeleteService().getServiceInterface().getInputDefinition().newInstance();
								serviceInput.set("id", deleteId);
								serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
								serviceInput.set("connectionId", connectionId);
								serviceInput.set("transactionId", transactionId);
//								serviceInput.set("language", language);
								serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
								ServiceRuntime runtime = new ServiceRuntime(service, executionContext);
								runtime.run(serviceInput);
							}
						}
					break;
				}
				return output;
			}
		};
	}

	@Override
	public Set<String> getReferences() {
		return null;
	}

	@Override
	public String getId() {
		return id;
	}

}
