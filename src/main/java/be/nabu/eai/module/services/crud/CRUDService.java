package be.nabu.eai.module.services.crud;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.nabu.eai.module.services.crud.api.CRUDListAction;
import be.nabu.eai.module.services.crud.api.CRUDProvider;
import be.nabu.eai.module.services.crud.provider.CRUDMeta;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.module.web.application.api.PermissionWithRole;
import be.nabu.eai.module.web.application.api.RESTFragment;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.util.Filter;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.artifacts.api.ArtifactWithExceptions;
import be.nabu.libs.artifacts.api.ExceptionDescription;
import be.nabu.libs.authentication.api.Permission;
import be.nabu.libs.events.api.EventSubscription;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.server.HTTPServerUtils;
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
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.TypeBaseUtils;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.mask.MaskedContent;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.GeneratedProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.PrimaryKeyProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;

// TODO: allow orderby configuration
// TODO: allow for fields to be filled in on creation (e.g. the optional verified dateTime in a component)
// TODO: allow "soft" delete -> mark a field for soft deletion, always take it into effect when selecting and use it to perform a soft delete
// to support cms nodes -> a) "managed" fields, the provider is extended with a "managed" fields option where you can add fields that will be automatically managed
//		-> maybe include an extension requirement in this? basically we say you have to extend a certain document, we list fields from there
// -> we want additional configuration (perhaps a configuration document that is configured in the provider?)
// -> for example for CMS nodes you can configure the groups/roles etc

// TODO: provider parameters: allow to fix fill in for a CRUD artifact
// TODO: provider parameters: allow to fill in via query params? or choose to expose or not
public class CRUDService implements DefinedService, WebFragment, RESTFragment, ArtifactWithExceptions {

	public static List<String> inputOperators = Arrays.asList("=", "<>", ">", "<", ">=", "<=", "like", "ilike");
	public static List<String> operators = Arrays.asList("=", "<>", ">", "<", ">=", "<=", "is null", "is not null", "like", "ilike");
	
	private String id;
	private CRUDType type;
	private Structure input, output;
	private DefinedStructure createInput;
	private DefinedStructure updateInput;
	private DefinedStructure outputList;
	private CRUDArtifact artifact;
	private DefinedStructure updateIntermediaryInput;
	private DefinedStructure singleOutput;
	private DefinedStructure createOutput;
	private DefinedStructure updateOutput;
	private CRUDListAction listAction;
	
	public enum CRUDType {
		CREATE,
		LIST,
		UPDATE,
		DELETE,
		GET
	}

	public CRUDService(CRUDArtifact artifact, String id, CRUDType type, DefinedStructure createInput, DefinedStructure updateInput, DefinedStructure outputList, DefinedStructure updateIntermediaryInput, DefinedStructure singleOutput, DefinedStructure createOutput, DefinedStructure updateOutput,
			CRUDListAction listAction) {
		this.artifact = artifact;
		this.id = id;
		this.type = type;
		this.createInput = createInput;
		this.updateInput = updateInput;
		this.outputList = outputList;
		this.updateIntermediaryInput = updateIntermediaryInput;
		this.singleOutput = singleOutput;
		this.createOutput = createOutput;
		this.updateOutput = updateOutput;
		this.listAction = listAction;
	}
	
	public List<CRUDFilter> getFilters() {
		return listAction.getFilters();
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
				return getDefinedOutput();
			}
			@Override
			public ComplexType getInputDefinition() {
				return getDefinedInput();
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
				
				// if we have configured a connection id, use that
				if (connectionId == null && artifact.getConfig().getConnection() != null) {
					connectionId = artifact.getConfig().getConnection().getId();
				}
				
				ComplexContent output = getServiceInterface().getOutputDefinition().newInstance();
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
									else if (String.class.isAssignableFrom(((SimpleType<?>) element.getType()).getInstanceClass())) {
										// we also set a uuid for a missing primary string key
										createInstance.set(element.getName(), UUID.randomUUID().toString().replace("-", ""));
									}
								}
								// if we have a date, we initiate it as "current", we assume something like created or modified
								else if (Date.class.isAssignableFrom(((SimpleType<?>) element.getType()).getInstanceClass())) {
									createInstance.set(element.getName(), new Date());
								}
								// booleans are false by default
								else if (Boolean.class.isAssignableFrom(((SimpleType<?>) element.getType()).getInstanceClass())) {
									createInstance.set(element.getName(), false);
								}
							}
						}
						// if we have a parent id, set it
						Element<?> securityContext = getSecurityContext();
						if (securityContext != null) {
							createInstance.set(securityContext.getName(), input.get("contextId"));
						}
						serviceInput.set("instance", createInstance);
						serviceInput.set("connectionId", connectionId);
						serviceInput.set("transactionId", transactionId);
						serviceInput.set("language", language);
						serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
						serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
						
						output.set("created", new MaskedContent(createInstance, createOutput));
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
						serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
						if (artifact.getConfig().isUseLanguage()) {
							serviceInput.set("language", language);
						}
						serviceInput.set("changeTracker", artifact.getConfig().getChangeTracker() == null ? null : artifact.getConfig().getChangeTracker().getId());
						
						output.set("updated", new MaskedContent(updateInstance, updateOutput));
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
					case GET:
						Object id = input == null ? null : input.get("id");
						if (id == null) {
							return null;
						}
						serviceInput = artifact.getConfig().getProvider().getConfig().getListService().getServiceInterface().getInputDefinition().newInstance();
						serviceInput.set("typeId", singleOutput.getId());
						serviceInput.set("connectionId", connectionId);
						serviceInput.set("transactionId", transactionId);
						if (artifact.getConfig().isUseLanguage()) {
							serviceInput.set("language", language);
						}
						serviceInput.set("limit", 1);
						CRUDFilter idFilter = new CRUDFilter();
						Element<?> typePrimary = getPrimary((ComplexType) artifact.getConfig().getCoreType());
						if (typePrimary == null) {
							throw new IllegalStateException("Can not find primary key");
						}
						idFilter.setKey(typePrimary.getName());
						idFilter.setOperator("=");
						idFilter.setOr(false);
						idFilter.setCaseInsensitive(false);
						idFilter.setValues(Arrays.asList(id));
						serviceInput.set("filters[0]", idFilter);
					break;
					case LIST:
						serviceInput = artifact.getConfig().getProvider().getConfig().getListService().getServiceInterface().getInputDefinition().newInstance();
//						serviceInput.set("typeId", artifact.getConfig().getCoreType().getId());
						// we want to use the extended output (with necessary restrictions but also foreign key extensions) to be used as basis for the select
						serviceInput.set("typeId", singleOutput.getId());
						serviceInput.set("connectionId", connectionId);
						serviceInput.set("transactionId", transactionId);
						if (artifact.getConfig().isUseLanguage()) {
							serviceInput.set("language", language);
						}
						// we set the max limit if none is provided
						// the max limit itself can be null
						Integer limit = input == null ? null : (Integer) input.get("limit");
						if (listAction.getMaxLimit() != null) {
							limit = limit == null ? listAction.getMaxLimit() : Math.min(limit, listAction.getMaxLimit());
						}
						serviceInput.set("limit", limit);
						serviceInput.set("offset", input == null ? null : input.get("offset"));
						serviceInput.set("orderBy", input == null ? null : input.get("orderBy"));
						serviceInput.set("limitToUser", input == null ? null : input.get("limitToUser"));
						serviceInput.set("lazy", input == null ? null : input.get("lazy"));
						List<Filter> filters = new ArrayList<Filter>();
						if (listAction.getFilters() != null) {
							transformFilters(listAction.getFilters(), input, filters);
						}
						serviceInput.set("filters", filters);
					break;
				}
				
				serviceInput.set("meta", getMeta());
				
				Object providerInput = input.get("provider");
				// if we have provider input, we must pass it along
				if (providerInput != null) {
					if (!(providerInput instanceof ComplexContent)) {
						providerInput = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(providerInput);
					}
					for (Element<?> child : TypeUtils.getAllChildren(((ComplexContent) providerInput).getType())) {
						serviceInput.set(child.getName(), ((ComplexContent) providerInput).get(child.getName()));
					}
				}
				
				ServiceRuntime runtime = new ServiceRuntime(service, executionContext);
				ComplexContent serviceOutput = runtime.run(serviceInput);
				
				switch(type) {
					case LIST: 
						if (serviceOutput.get("results") != null) {
							ListResult result = TypeUtils.getAsBean((ComplexContent) serviceOutput.get("results"), ListResult.class);
							if (result.getResults() != null && !result.getResults().isEmpty()) {
								output.set("results", result.getResults());
							}
							// if we have a total row count, build the page object
							if (result.getTotalRowCount() != null) {
								Integer limit = input == null ? null : (Integer) input.get("limit");
								if (listAction.getMaxLimit() != null) {
									limit = limit == null ? listAction.getMaxLimit() : Math.min(limit, listAction.getMaxLimit());
								}
								output.set("page", Page.build(result.getTotalRowCount(), input == null ? null : (Long) input.get("offset"), limit));
							}
							else {
								Long rowCount = result.getRowCount();
								if (rowCount == null) {
									rowCount = result.getResults() != null ? result.getResults().size() : 0l;
								}
								output.set("page", Page.build(rowCount, 0l, rowCount.intValue()));
							}
						}
					break;
					case GET:
						if (serviceOutput.get("results") != null) {
							ListResult result = TypeUtils.getAsBean((ComplexContent) serviceOutput.get("results"), ListResult.class);
							if (result.getResults() != null && !result.getResults().isEmpty()) {
								output.set("result", result.getResults().get(0));
							}
						}
					break;
					case UPDATE:
					case CREATE:
						artifact.checkBroadcast(executionContext, connectionId, transactionId, (ComplexContent) serviceInput.get("instance"), type == CRUDType.UPDATE, true);
					break;
				}
				return output;
			}

		};
	}
	
	private CRUDMeta getMeta() {
		CRUDMeta meta = new CRUDMeta();
		meta.setPermissionAction(getPermissionAction());
		Element<?> securityContext = getSecurityContext();
		if (securityContext != null) {
			meta.setSecurityField(securityContext.getName());
		}
		return meta;
	}
	
	public static void transformFilters(List<CRUDFilter> sourceFilters, ComplexContent input, List<Filter> targetFilters) {
		transformFilters(sourceFilters, input, targetFilters, true);
	}
	
	@SuppressWarnings("unchecked")
	public static void transformFilters(List<CRUDFilter> sourceFilters, ComplexContent input, List<Filter> targetFilters, boolean addSqlWildcard) {
		// the previous removed one
		CRUDFilter removed = null;
		for (CRUDFilter filter : sourceFilters) {
			// no operator or no key is invalid
			// additionally if you have an input operator but don't have the flag "isinput" checked, it can only produce invalid results
			if (filter == null || filter.getOperator() == null || filter.getKey() == null
					|| (!filter.isInput() && inputOperators.contains(filter.getOperator()))) {
				continue;
			}
			// we don't want the ilike statements to make it to the end
			CRUDFilter newFilter = new CRUDFilter();
			newFilter.setVary(filter.isVary());
			newFilter.setKey(filter.getKey());
			newFilter.setOperator("ilike".equals(filter.getOperator()) ? "like" : filter.getOperator());
			newFilter.setCaseInsensitive(filter.isCaseInsensitive() || "ilike".equals(filter.getOperator()));
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
				// if we have a like, add "%"
				if ("like".equals(newFilter.getOperator())) {
					List<Object> wildCardValues = new ArrayList<Object>();
					for (Object value : values) {
						if (value instanceof String && addSqlWildcard) {
							value = "%" + value.toString() + "%";
						}
						wildCardValues.add(value);
					}
					newFilter.setValues(wildCardValues);
					values = wildCardValues;
				}
				
				// if we have a boolean operator which is set as input and we _don't_ have input, we explicitly set the value null
				// this causes it to be filtered out when we send it to "selectFiltered". if we leave the values empty, the filter will be applied
				if (values.isEmpty() && ("is null".equals(filter.getOperator()) || "is not null".equals(filter.getOperator()))) {
					values.add(null);
				}
			}
			// if it is not an input, or actual input was provided, do it
			if (!filter.isInput() || !values.isEmpty()) {
				targetFilters.add(newFilter);
				removed = null;
			}
			else {
				removed = filter;
			}
		}
	}

	@Override
	public Set<String> getReferences() {
		return null;
	}

	@Override
	public String getId() {
		return id;
	}

	// the parameters that are added to the input by the implementation service, this can allow for dynamic behavior
	private Structure providerParameters = null;
	private boolean providerParametersResolved = false;
	private Structure getProviderParameters() {
		if (!providerParametersResolved) {
			synchronized(this) {
				if (!providerParametersResolved) {
					Structure structure = new Structure();
					structure.setName("provider");
					List<Element<?>> inputExtensions;
					switch (type) {
						case CREATE:
							inputExtensions = EAIRepositoryUtils.getInputExtensions(artifact.getConfig().getProvider().getConfig().getCreateService(), EAIRepositoryUtils.getMethod(CRUDProvider.class, "create"));
						break;
						case UPDATE: 
							inputExtensions = EAIRepositoryUtils.getInputExtensions(artifact.getConfig().getProvider().getConfig().getUpdateService(), EAIRepositoryUtils.getMethod(CRUDProvider.class, "update"));
						break;
						case DELETE:
							inputExtensions = EAIRepositoryUtils.getInputExtensions(artifact.getConfig().getProvider().getConfig().getDeleteService(), EAIRepositoryUtils.getMethod(CRUDProvider.class, "delete"));
						break;
						default:
							inputExtensions = EAIRepositoryUtils.getInputExtensions(artifact.getConfig().getProvider().getConfig().getListService(), EAIRepositoryUtils.getMethod(CRUDProvider.class, "list"));
					}
					for (Element<?> extension : inputExtensions) {
						structure.add(TypeBaseUtils.clone(extension, structure));
					}
					this.providerParameters = inputExtensions.isEmpty() ? null : structure;
					this.providerParametersResolved = true;
				}
			}
		}
		return providerParameters;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Structure getDefinedInput() {
		if (input == null) {
			Structure input = new Structure();
			input.setName("input");
			input.add(new SimpleElementImpl<String>("connectionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			input.add(new SimpleElementImpl<String>("transactionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			Element<?> primary = getPrimary((ComplexType) artifact.getConfig().getCoreType());
			Element<?> securityContext = getSecurityContext();
			switch(type) {
				case CREATE:
					// we want to capture the parent id separately because this might have security implications in our REST model
					// we "could" keep them together for non-rest calls but that is making it harder than it has to be...
					if (securityContext != null) {
						SimpleElementImpl parentElement = new SimpleElementImpl("contextId", (SimpleType<?>) securityContext.getType(), input);
						Value<Integer> property = securityContext.getProperty(MinOccursProperty.getInstance());
						// we want it to be optional input if it is optional in the data type
						if (property != null) {
							parentElement.setProperty(property);
						}
						input.add(parentElement);
					}
					input.add(new ComplexElementImpl("instance", createInput, input));
				break;
				case UPDATE:
					if (artifact.getConfig().isUseLanguage()) {
						input.add(new SimpleElementImpl<String>("language", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
					input.add(new SimpleElementImpl("id", (SimpleType<?>) primary.getType(), input));
					input.add(new ComplexElementImpl("instance", updateInput, input));
				break;
				case DELETE:
					input.add(new SimpleElementImpl("id", (SimpleType<?>) primary.getType(), input));
				break;
				case GET:
					input.add(new SimpleElementImpl("id", (SimpleType<?>) primary.getType(), input));
					if (artifact.getConfig().isUseLanguage()) {
						input.add(new SimpleElementImpl<String>("language", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
				break;
				case LIST:
					// we don't add the "contextId" as input explicitly
					// you can choose via the filters whether you want to add it or not
					// if you set a security context _and_ add the context as a filter, it will be properly exposed (also through REST)
					if (artifact.getConfig().isUseLanguage()) {
						input.add(new SimpleElementImpl<String>("language", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
					input.add(new SimpleElementImpl<Integer>("limit", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Integer.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<Long>("offset", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<String>("orderBy", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<Boolean>("limitToUser", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Boolean.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<String>(CommentProperty.getInstance(), "If you enable this, the provider should try to limit the result set to data that the current user is allowed to see")));
					input.add(new SimpleElementImpl<Boolean>("lazy", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Boolean.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<String>(CommentProperty.getInstance(), "If you enable lazy mode, the provider will try to provide a lazy list, if it can not, a full list might be returned")));
					if (listAction.getFilters() != null) {
						Structure filters = new Structure();
						filters.setName("filter");
						for (CRUDFilter filter : listAction.getFilters()) {
							if (filter != null && filter.isInput() && filter.getKey() != null) {
								Element<?> element = ((ComplexType) artifact.getConfig().getCoreType()).get(filter.getKey());
								// if the element does not exist in the core type, it may have been added via foreign fields
								if (element == null) {
									element = singleOutput.get(filter.getKey());
								}
								// old filters might still have outdated values
								if (element != null) {
									// in most cases, the input type is the same as the element type _except_ when we are doing "is null" or "is not null" checks
									// in such scenarios, the resulting input field (if it is indeed marked as an input) is a boolean indicating whether or not you want the additional query to be active
									SimpleElementImpl childElement;
									// every other "new operator" that you typed is also considered to be an "is" check, like if you manually type "> current_timestamp"
									if ("is null".equals(filter.getOperator()) || "is not null".equals(filter.getOperator()) || !operators.contains(filter.getOperator())) {
										childElement = new SimpleElementImpl(filter.getAlias() == null ? filter.getKey() : filter.getAlias(), (SimpleType<Boolean>) SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Boolean.class), filters, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));	
									}
									else {
										childElement = new SimpleElementImpl(filter.getAlias() == null ? filter.getKey() : filter.getAlias(), (SimpleType<?>) element.getType(), filters, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));
									}
									// only for some filters do we support the list entries
									if ("=".equals(filter.getOperator()) || "<>".equals(filter.getOperator())) {
										// for boolean data types a list also makes little sense
										if (!Boolean.class.isAssignableFrom(((SimpleType<?>) childElement.getType()).getInstanceClass())) {
											childElement.setProperty(new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0));
										}
									}
									filters.add(childElement);
								}
							}
						}
						input.add(new ComplexElementImpl("filter", filters, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
				break;
			}
			Structure providerParameters = getProviderParameters();
			if (providerParameters != null) {
				// the provider should not have mandatory inputs?
				input.add(new ComplexElementImpl("provider", providerParameters, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			this.input = input;
		}
		return input;
	}
	
	static Element<?> getPrimary(ComplexType parent) {
		for (Element<?> element : TypeUtils.getAllChildren(parent)) {
			Value<Boolean> property = element.getProperty(PrimaryKeyProperty.getInstance());
			if (property != null && property.getValue() != null && property.getValue()) {
				return element;
			}
		}
		return null;
	}
	
	protected Element<?> getSecurityContext() {
		if (artifact.getConfig().getSecurityContextField() != null) {
			Element<?> element = ((ComplexType) artifact.getConfig().getCoreType()).get(artifact.getConfig().getSecurityContextField());
			if (element == null && (type == CRUDType.LIST || type == CRUDType.GET) && singleOutput != null) {
				element = singleOutput.get(artifact.getConfig().getSecurityContextField());
			}
			return element;
		}
		return null;
	}
	
	private Structure getDefinedOutput() {
		if (output == null) {
			Structure output = new Structure();
			output.setName("output");
			
			switch(type) {
				case CREATE:
					output.add(new ComplexElementImpl("created", createOutput, output));
				break;
				case UPDATE:
					output.add(new ComplexElementImpl("updated", updateOutput, output));
				break;
				case LIST:
					output.setSuperType(outputList);
				break;
				case GET:
					output.add(new ComplexElementImpl("result", singleOutput, output));
				break;
			}
			
			this.output = output;
		}
		return output;
	}
	
	private Map<String, EventSubscription<?, ?>> subscriptions = new HashMap<String, EventSubscription<?, ?>>();

	private String getKey(WebApplication artifact, String path) {
		return artifact.getId() + ":" + path;
	}
	
	public CRUDType getType() {
		return type;
	}

	@Override
	public void start(WebApplication artifact, String path) throws IOException {
		String key = getKey(artifact, path);
		if (subscriptions.containsKey(key)) {
			stop(artifact, path);
		}
		String restPath = artifact.getServerPath();
		if (path != null && !path.isEmpty() && !path.equals("/")) {
			if (!restPath.endsWith("/")) {
				restPath += "/";
			}
			restPath += path.replaceFirst("^[/]+", "");
		}
		String parentPath = restPath;
		if (this.artifact.getConfig().getBasePath() != null) {
			if (!restPath.endsWith("/")) {
				restPath += "/";
			}
			restPath += this.artifact.getConfig().getBasePath().replaceFirst("^[/]+", "");
		}
		synchronized(subscriptions) {
			CRUDListener listener = new CRUDListener(artifact, this.artifact, this, parentPath, getPath(), Charset.forName("UTF-8"));
			EventSubscription<HTTPRequest, HTTPResponse> subscription = artifact.getDispatcher().subscribe(HTTPRequest.class, listener);
			subscription.filter(HTTPServerUtils.limitToPath(restPath));
			subscriptions.put(key, subscription);
		}
	}

	@Override
	public void stop(WebApplication artifact, String path) {
		String key = getKey(artifact, path);
		if (subscriptions.containsKey(key)) {
			synchronized(subscriptions) {
				if (subscriptions.containsKey(key)) {
					subscriptions.get(key).unsubscribe();
					subscriptions.remove(key);
				}
			}
		}
	}
	
	public void unsubscribeAll() {
		synchronized(subscriptions) {
			for (EventSubscription<?, ?> sub : subscriptions.values()) {
				sub.unsubscribe();
			}
			subscriptions.clear();
		}
	}
	
	@Override
	public String getPermissionAction() {
		String name = getName();
		switch (type) {
			case CREATE:
				return name + ".create";
			case DELETE: 
				return name + ".delete";
			case LIST:
				return name + ".list" + (listAction.getName() == null ? "" : CRUDArtifactManager.getViewName(listAction.getName()));
			case UPDATE:
				return name + ".update";
			case GET:
				return name + ".get" + (listAction.getName() == null ? "" : CRUDArtifactManager.getViewName(listAction.getName()));
		}
		return null;
	}

	@Override
	public List<Permission> getPermissions(WebApplication artifact, String path) {
		List<Permission> permissions = new ArrayList<Permission>();
		switch (type) {
			case CREATE:
				permissions.add(new PermissionImplementation(null, getPermissionAction(), this.artifact.getConfig().getCreateRole()));
			break;
			case DELETE: 
				permissions.add(new PermissionImplementation(null, getPermissionAction(), this.artifact.getConfig().getDeleteRole()));
			break;
			case LIST:
				permissions.add(new PermissionImplementation(null, getPermissionAction(), this.artifact.getConfig().getListRole()));
			break;
			case UPDATE:
				permissions.add(new PermissionImplementation(null, getPermissionAction(), this.artifact.getConfig().getUpdateRole()));
			break;
			case GET:
				permissions.add(new PermissionImplementation(null, getPermissionAction(), this.artifact.getConfig().getListRole()));
			break;
		}
		return permissions;
	}
	
	@Override
	public boolean isStarted(WebApplication artifact, String path) {
		return subscriptions.containsKey(getKey(artifact, path));
	}
	
	public static class PermissionImplementation implements PermissionWithRole {
		
		private String context;
		private String action;
		private List<String> roles;

		public PermissionImplementation() {
			// auto
		}
		
		public PermissionImplementation(String context, String action, List<String> roles) {
			this.context = context;
			this.action = action;
			this.roles = roles;
		}
		
		@Override
		public String getContext() {
			return context;
		}

		public void setContext(String context) {
			this.context = context;
		}

		@Override
		public String getAction() {
			return action;
		}
		public void setAction(String action) {
			this.action = action;
		}

		@Override
		public List<String> getRoles() {
			return roles;
		}
		public void setRoles(List<String> roles) {
			this.roles = roles;
		}
	}
	
	private String getName() {
		String name = artifact.getConfig().getName();
		if (name == null || name.trim().isEmpty()) {
			name = artifact.getConfig().getCoreType().getName();
		}
		return name;
	}

	@Override
	public String getPath() {
		String path = artifact.getConfig().getBasePath() == null ? "" : artifact.getConfig().getBasePath();
		if (!path.isEmpty() && !path.endsWith("/")) { 
			path += "/";
		}
		// we don't want to start a new subpath, it is likely matched by id for get etc
		// instead it should be something along the lines of "organisationTest" or whatever
		String suffix = listAction != null && listAction.getName() != null ? CRUDArtifactManager.getViewName(listAction.getName()) : "";
		
		switch(type) {
			case GET:
			case UPDATE:
			case DELETE:
				// if we have a security context and the id is not a valid one, add it
				return path + (getSecurityContext() != null && !artifact.getConfig().getProvider().isPrimaryKeySecurityContext() ? "{contextId}/" : "") + getName() + suffix + "/{id}";
			case CREATE:
				if (getSecurityContext() != null) {
					path += "{contextId}/";
				}
				path += getName();
				return path;
			// if we have a filter that does an "=" on the parent field, we want it in the path for proper REST design
			default:
				if (hasSecurityContextFilter()) {
					path += "{contextId}/";
				}
				path += getName();
				return path + suffix;
		}
	}

	public boolean hasSecurityContextFilter() {
		boolean hasParent = false;
		if (listAction.getFilters() != null && artifact.getConfig().getSecurityContextField() != null) {
			for (CRUDFilter filter : listAction.getFilters()) {
				if (filter != null && filter.getKey() != null && artifact.getConfig().getSecurityContextField().equals(filter.getKey()) && "=".equals(filter.getOperator())) {
					hasParent = true;
					break;
				}
			}
		}
		return hasParent;
	}

	@Override
	public String getMethod() {
		switch(type) {
			case CREATE: return "POST";
			case UPDATE: return "PUT";
			case DELETE: return "DELETE";
			default: return "GET";
		}
	}

	@Override
	public List<String> getConsumes() {
		return Arrays.asList("application/json", "application/xml");
	}

	@Override
	public List<String> getProduces() {
		return Arrays.asList("application/json", "application/xml");
	}

	@Override
	public Type getInput() {
		switch(type) {
			case CREATE: return createInput;
			case UPDATE: return updateInput;
			default: return null;
		}
	}

	@Override
	public Type getOutput() {
		switch (type) {
			case LIST: return outputList;
			case GET: return singleOutput;
			case CREATE: return createOutput;
			case UPDATE: return updateOutput;
			default: return null;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public List<Element<?>> getQueryParameters() {
		List<Element<?>> parameters = new ArrayList<Element<?>>();
		// only the list has query parameters
		if (type == CRUDType.LIST) {
			Structure input = new Structure();
			parameters.add(new SimpleElementImpl<Integer>("limit", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Integer.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			parameters.add(new SimpleElementImpl<Long>("offset", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			parameters.add(new SimpleElementImpl<String>("orderBy", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
			if (artifact.getConfig().isUseLanguage() && artifact.getConfig().isUseExplicitLanguage()) {
				parameters.add(new SimpleElementImpl<String>("language", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			if (listAction.getFilters() != null) {
				Element<?> parent = getSecurityContext();
				List<String> alreadyDefined = new ArrayList<String>();
				for (CRUDFilter filter : listAction.getFilters()) {
					if (filter != null && filter.getKey() != null && filter.isInput()) {
						// if we have a list service which has a parent id filter, we put it in the path, not in the query
						if (parent != null && parent.getName().equals(filter.getKey())) {
							continue;
						}
						// it might be an extension!
						Element<?> element = singleOutput.get(filter.getKey());
						if (element == null) {
							element = ((ComplexType) artifact.getConfig().getCoreType()).get(filter.getKey());
						}
						// you might be referencing an item that no longer exists
						if (element == null) {
							continue;
						}
						SimpleElementImpl childElement = new SimpleElementImpl(filter.getAlias() == null ? filter.getKey() : filter.getAlias(), (SimpleType<?>) element.getType(), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));
						// we can reuse the same filter with the same name for multiple matches, we however only want to expose it once
						if (alreadyDefined.contains(childElement.getName())) {
							continue;
						}
						else {
							alreadyDefined.add(childElement.getName());
						}
						// only for some filters do we support the list entries
						if ("=".equals(filter.getOperator()) || "<>".equals(filter.getOperator())) {
							childElement.setProperty(new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0));
						}
						parameters.add(childElement);
					}
				}
			}
		}
		else if (type == CRUDType.UPDATE && artifact.getConfig().isUseLanguage() && artifact.getConfig().isUseExplicitLanguage()) {
			Structure input = new Structure();
			parameters.add(new SimpleElementImpl<String>("language", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
		}
		return parameters;
	}

	@Override
	public List<Element<?>> getHeaderParameters() {
		return new ArrayList<Element<?>>();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public List<Element<?>> getPathParameters() {
		Structure input = new Structure();
		List<Element<?>> parameters = new ArrayList<Element<?>>();
		Element<?> primary = getPrimary((ComplexType) artifact.getConfig().getCoreType());
		Element<?> securityContext = getSecurityContext();
		switch(type) {
			case GET:
			case DELETE: 
			case UPDATE: 
				parameters.add(new SimpleElementImpl("id", (SimpleType) primary.getType(), input)); 
				if (securityContext != null && !artifact.getConfig().getProvider().isPrimaryKeySecurityContext()) {
					parameters.add(new SimpleElementImpl("contextId", (SimpleType) securityContext.getType(), input));
				}
			break;
			case CREATE:
				if (securityContext != null) {
					parameters.add(new SimpleElementImpl("contextId", (SimpleType) securityContext.getType(), input));
				}
			break;
			default: 
				if (securityContext != null && hasSecurityContextFilter()) {
					parameters.add(new SimpleElementImpl("contextId", (SimpleType) securityContext.getType(), input));
				}
		}
		return parameters;
	}

	@Override
	public List<ExceptionDescription> getExceptions() {
		Service service;
		switch(type) {
			case CREATE: service = artifact.getConfig().getProvider().getConfig().getCreateService(); break;
			case UPDATE: service = artifact.getConfig().getProvider().getConfig().getUpdateService(); break;
			case DELETE: service = artifact.getConfig().getProvider().getConfig().getDeleteService(); break;
			default: service = artifact.getConfig().getProvider().getConfig().getListService(); 
		}
		return !(service instanceof ArtifactWithExceptions) ? null : ((ArtifactWithExceptions) service).getExceptions();
	}
	
	protected CRUDListAction getCRUDListAction() {
		return listAction;
	}

	@Override
	public Map<String, Object> getExtensions() {
		Map<String, Object> extensions = new HashMap<String, Object>();
		// create are only relevant for LIST
		if (listAction != null && listAction.isBroadcastCreate() && getType().equals(CRUDType.LIST)) {
			extensions.put("stream-create", "true");
		}
		if (listAction != null && listAction.isBroadcastUpdate()) {
			extensions.put("stream-update", "true");
		}
		return extensions;
	}
	
}
