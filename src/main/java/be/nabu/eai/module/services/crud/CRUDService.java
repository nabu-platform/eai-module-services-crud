package be.nabu.eai.module.services.crud;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.mask.MaskedContent;
import be.nabu.libs.types.properties.GeneratedProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.PrimaryKeyProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;

// TODO: allow orderby configuration
// TODO: allow "soft" delete -> mark a field for soft deletion, always take it into effect when selecting and use it to perform a soft delete
// to support cms nodes -> a) "managed" fields, the provider is extended with a "managed" fields option where you can add fields that will be automatically managed
//		-> maybe include an extension requirement in this? basically we say you have to extend a certain document, we list fields from there
// -> we want additional configuration (perhaps a configuration document that is configured in the provider?)
// -> for example for CMS nodes you can configure the groups/roles etc
public class CRUDService implements DefinedService {

	private String id;
	private CRUDType type;
	private Structure input, output;
	private DefinedStructure createInput;
	private DefinedStructure updateInput;
	private DefinedStructure outputList;
	private CRUDArtifact artifact;
	private DefinedStructure updateIntermediaryInput;
	
	public enum CRUDType {
		CREATE,
		LIST,
		UPDATE,
		DELETE
	}

	public CRUDService(CRUDArtifact artifact, String id, CRUDType type, DefinedStructure createInput, DefinedStructure updateInput, DefinedStructure outputList, DefinedStructure updateIntermediaryInput) {
		this.artifact = artifact;
		this.id = id;
		this.type = type;
		this.createInput = createInput;
		this.updateInput = updateInput;
		this.outputList = outputList;
		this.updateIntermediaryInput = updateIntermediaryInput;
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
				return CRUDService.this;
			}
			
			@SuppressWarnings("unchecked")
			@Override
			public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
				Service service;
				switch(type) {
					case CREATE: service = artifact.getConfig().getProvider().getConfig().getCreateService(); break;
					case UPDATE: service = artifact.getConfig().getProvider().getConfig().getUpdateService(); break;
					case DELETE: service = artifact.getConfig().getProvider().getConfig().getDeleteService(); break;
					default: service = artifact.getConfig().getProvider().getConfig().getListService(); 
				}
				String connectionId = input == null ? null : (String) input.get("connectionId");
				String transactionId = input == null ? null : (String) input.get("transactionId");
				String language = input == null ? null : (String) input.get("language");
				
				ComplexContent serviceInput = null;
				Object object;
				switch(type) {
					case CREATE:
						object = input == null ? null : input.get("instance");
						if (object != null) {
							if (!(object instanceof ComplexContent)) {
								object = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(object);
							}
						}
						serviceInput = artifact.getConfig().getProvider().getConfig().getCreateService().getServiceInterface().getInputDefinition().newInstance();
						// we might need to generate some new values
						MaskedContent createInstance = new MaskedContent((ComplexContent) object, (ComplexType) artifact.getConfig().getCoreType());
						for (Element<?> element : TypeUtils.getAllChildren((ComplexType) artifact.getConfig().getCoreType())) {
							Value<Integer> minOccurs = element.getProperty(MinOccursProperty.getInstance());
							Value<Boolean> generated = element.getProperty(GeneratedProperty.getInstance());
							// we never mess with generated
							if (generated != null && generated.getValue() != null && generated.getValue()) {
								continue;
							}
							// if it has no value and it is mandatory, we might need to add "some" value
							else if (createInstance.get(element.getName()) == null && (minOccurs == null || minOccurs.getValue() == null || minOccurs.getValue() >= 1)) {
								Value<Boolean> primary = element.getProperty(PrimaryKeyProperty.getInstance());
								// if we have a primary key, we can generate a uuid (if it is a uuid)
								if (primary != null && primary.getValue() != null && primary.getValue()) {
									if (UUID.class.isAssignableFrom(((SimpleType<?>) element.getType()).getInstanceClass())) {
										createInstance.set(element.getName(), UUID.randomUUID());
									}
								}
								// if we have a date, we initiate it as "current", we assume something like created or modified
								else if (Date.class.isAssignableFrom(((SimpleType<?>) element.getType()).getInstanceClass())) {
									createInstance.set(element.getName(), new Date());
								}
							}
						}
						serviceInput.set("instance", createInstance);
						serviceInput.set("connectionId", connectionId);
						serviceInput.set("transactionId", transactionId);
						serviceInput.set("language", language);
						serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
					break;
					case UPDATE:
						object = input == null ? null : input.get("instance");
						if (object != null) {
							if (!(object instanceof ComplexContent)) {
								object = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(object);
							}
						}
						
						// we want to map the primary key and optionally add some other things
						MaskedContent updateInstance = new MaskedContent((ComplexContent) object, updateIntermediaryInput);
						Element<?> primary = getPrimary(updateInstance.getType());
						if (primary == null) {
							throw new IllegalStateException("Could not find primary key field definition for updating");
						}
						Object updateId = input == null ? null : input.get("id");
						if (updateId == null) {
							throw new IllegalStateException("Could not find primary key field value for updating");
						}
						updateInstance.set(primary.getName(), updateId);
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
						serviceInput.set("language", language);
						serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
					break;
					case DELETE:
						serviceInput = artifact.getConfig().getProvider().getConfig().getDeleteService().getServiceInterface().getInputDefinition().newInstance();
						Object deleteId = input == null ? null : input.get("id");
						if (deleteId == null) {
							throw new IllegalStateException("Could not find primary key field value for updating");
						}
						serviceInput.set("id", deleteId);
						serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
						serviceInput.set("connectionId", connectionId);
						serviceInput.set("transactionId", transactionId);
//						serviceInput.set("language", language);
						serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
					break;
					case LIST:
						serviceInput = artifact.getConfig().getProvider().getConfig().getListService().getServiceInterface().getInputDefinition().newInstance();
						serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
						serviceInput.set("connectionId", connectionId);
						serviceInput.set("transactionId", transactionId);
						serviceInput.set("language", language);
						serviceInput.set("limit", input == null ? null : input.get("limit"));
						serviceInput.set("offset", input == null ? null : input.get("offset"));
						serviceInput.set("orderBy", input == null ? null : input.get("orderBy"));
						List<CRUDFilter> filters = new ArrayList<CRUDFilter>();
						// the previous removed one
						CRUDFilter removed = null;
						if (artifact.getConfig().getFilters() != null) {
							for (CRUDFilter filter : artifact.getConfig().getFilters()) {
								CRUDFilter newFilter = new CRUDFilter();
								newFilter.setCaseInsensitive(filter.isCaseInsensitive());
								newFilter.setKey(filter.getKey());
								newFilter.setOperator(filter.getOperator());
								// if we removed the previous filter and it was an "and", we can't make this an or, cause the end result would not match
								// if the previous one was also an or (removed or not), it doesn't matter
								newFilter.setOr(filter.isOr() && (removed == null || removed.isOr()));
								List<Object> values = new ArrayList<Object>();
								newFilter.setValues(values);
								if (filter.isInput()) {
									Object inputtedValues = input == null ? null : input.get("filter/" + (filter.getAlias() == null ? filter.getKey() : filter.getAlias()));
									// could be a list or not a list
									if (inputtedValues instanceof Iterable) {
										for (Object inputtedValue : (Iterable<Object>) inputtedValues) {
											values.add(inputtedValue);
										}
									}
									else if (inputtedValues != null) {
										values.add(inputtedValues);
									}
								}
								// if it is not an input, or actual input was provided, do it
								if (!filter.isInput() || !values.isEmpty()) {
									filters.add(newFilter);
									removed = null;
								}
								else {
									removed = filter;
								}
							}
						}
						serviceInput.set("filters", filters);
					break;
				}
				
				ServiceRuntime runtime = new ServiceRuntime(service, executionContext);
				ComplexContent serviceOutput = runtime.run(serviceInput);
				
				ComplexContent output = getServiceInterface().getOutputDefinition().newInstance();
				// in case of the read, we have some stuff to do still
				switch(type) {
					case LIST: 
						if (serviceOutput.get("results") != null) {
							ListResult result = TypeUtils.getAsBean((ComplexContent) serviceOutput.get("results"), ListResult.class);
							if (result.getResults() != null && !result.getResults().isEmpty()) {
								output.set("results", result.getResults());
							}
							// if we have a total row count, build the page object
							if (result.getTotalRowCount() != null) {
								output.set("page", Page.build(result.getTotalRowCount(), input == null ? null : (Long) input.get("offset"), input == null ? null : (Integer) input.get("limit")));
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

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Structure getInput() {
		if (input == null) {
			Structure input = new Structure();
			input.setName("input");
			input.add(new SimpleElementImpl<String>("connectionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			input.add(new SimpleElementImpl<String>("transactionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			Element<?> primary = getPrimary((ComplexType) artifact.getConfig().getCoreType());
			switch(type) {
				case CREATE:
					input.add(new ComplexElementImpl("instance", createInput, input));
				break;
				case UPDATE:
					input.add(new SimpleElementImpl<String>("language", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl("id", (SimpleType<?>) primary.getType(), input));
					input.add(new ComplexElementImpl("instance", updateInput, input));
				break;
				case DELETE:
					input.add(new SimpleElementImpl("id", (SimpleType<?>) primary.getType(), input));
				break;
				case LIST:
					input.add(new SimpleElementImpl<String>("language", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<Integer>("limit", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Integer.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<Long>("offset", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<String>("orderBy", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
					if (artifact.getConfig().getFilters() != null) {
						Structure filters = new Structure();
						filters.setName("filter");
						for (CRUDFilter filter : artifact.getConfig().getFilters()) {
							if (filter.isInput()) {
								Element<?> element = ((ComplexType) artifact.getConfig().getCoreType()).get(filter.getKey());
								SimpleElementImpl childElement = new SimpleElementImpl(filter.getAlias() == null ? filter.getKey() : filter.getAlias(), (SimpleType<?>) element.getType(), filters, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));
								// only for some filters do we support the list entries
								if ("=".equals(filter.getOperator()) || "<>".equals(filter.getOperator())) {
									childElement.setProperty(new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0));
								}
								filters.add(childElement);
							}
						}
						input.add(new ComplexElementImpl("filter", filters, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
				break;
			}
			
			this.input = input;
		}
		return input;
	}
	
	private Element<?> getPrimary(ComplexType parent) {
		for (Element<?> element : TypeUtils.getAllChildren(parent)) {
			Value<Boolean> property = element.getProperty(PrimaryKeyProperty.getInstance());
			if (property != null && property.getValue() != null && property.getValue()) {
				return element;
			}
		}
		return null;
	}
	
	private Structure getOutput() {
		if (output == null) {
			Structure output = new Structure();
			output.setName("output");
			
			switch(type) {
				case LIST:
					output.setSuperType(outputList);
				break;
			}
			
			this.output = output;
		}
		return output;
	}
}
