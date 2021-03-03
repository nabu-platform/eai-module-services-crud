package be.nabu.eai.module.services.crud;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;
import be.nabu.eai.module.services.crud.CRUDService.CRUDType;
import be.nabu.eai.module.services.crud.provider.CRUDProviderArtifact;
import be.nabu.eai.repository.EAINode;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.ArtifactRepositoryManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ModifiableEntry;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.eai.repository.resources.MemoryEntry;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.TypeBaseUtils;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.properties.DuplicateProperty;
import be.nabu.libs.types.properties.ForeignKeyProperty;
import be.nabu.libs.types.properties.ForeignNameProperty;
import be.nabu.libs.types.properties.GeneratedProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.libs.types.properties.PrimaryKeyProperty;
import be.nabu.libs.types.properties.RestrictProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;

public class CRUDArtifactManager extends JAXBArtifactManager<CRUDConfiguration, CRUDArtifact> implements ArtifactRepositoryManager<CRUDArtifact> {

	public CRUDArtifactManager() {
		super(CRUDArtifact.class);
	}

	@Override
	protected CRUDArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new CRUDArtifact(id, container, repository);
	}
	
	@Override
	public List<Entry> addChildren(ModifiableEntry parent, CRUDArtifact artifact) throws IOException {
		List<Entry> entries = new ArrayList<Entry>();
		((EAINode) parent.getNode()).setLeaf(false);
		CRUDProviderArtifact provider = artifact.getConfig().getProvider();
		// create the types
		if (artifact.getConfig().getCoreType() != null) {
			ModifiableEntry types = EAIRepositoryUtils.getParent(parent, "types", true);
			DefinedStructure createInput = null, updateInput = null, outputList = null, updateIntermediaryInput = null, createOutput = null, updateOutput = null;
			// if we have a provider with a create, add it
			List<String> primary = getPrimary((ComplexType) artifact.getConfig().getCoreType());
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getCreateService() != null) {
				List<String> blacklist = artifact.getConfig().getCreateBlacklistFields();
				// let's add to that
				blacklist = blacklist == null ? new ArrayList<String>() : new ArrayList<String>(blacklist);
				if (provider != null && provider.getConfig().getBlacklistedFields() != null) {
					blacklist.addAll(provider.getConfig().getBlacklistedFields());
				}
				createOutput = addChild(artifact, entries, types, "createOutput", artifact.getConfig().getCoreType(), new ArrayList<String>(blacklist));
				synchronize(createOutput, (ComplexType) artifact.getConfig().getCoreType());
				
				// we don't need to blacklist again as we will build upon the output
				blacklist.clear();
				blacklist.addAll(primary);
				blacklist.addAll(getGenerated((ComplexType) artifact.getConfig().getCoreType()));
				if (artifact.getConfig().getSecurityContextField() != null) {
					blacklist.add(artifact.getConfig().getSecurityContextField());
				}
				// generate the input
				createInput = addChild(artifact, entries, types, "createInput", createOutput, blacklist);
				synchronize(createInput, (ComplexType) artifact.getConfig().getCoreType());
			}
			// if we have a provider with a create, add it
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getUpdateService() != null) {
				List<String> blacklist = artifact.getConfig().getUpdateBlacklistFields();
				// let's add to that
				blacklist = blacklist == null ? new ArrayList<String>() : new ArrayList<String>(blacklist);
				
				if (provider != null && provider.getConfig().getBlacklistedFields() != null) {
					blacklist.addAll(provider.getConfig().getBlacklistedFields());
				}
				// do allow all the fields we want to update
				if (artifact.getConfig().getUpdateRegenerateFields() != null) {
					blacklist.removeAll(artifact.getConfig().getUpdateRegenerateFields());
				}
				// we create an intermediary update structure that contains all the fields you input as well as the fields we need to add to make it work (the primary key & any refreshable fields)
				blacklist.addAll(getGenerated((ComplexType) artifact.getConfig().getCoreType()));
				// remove any generated that are also the primary, we need to keep the primary for update reference
				blacklist.removeAll(primary);
				updateIntermediaryInput = addChild(artifact, entries, types, "updateIntermediaryInput", artifact.getConfig().getCoreType(), new ArrayList<String>(blacklist));
				synchronize(updateIntermediaryInput, (ComplexType) artifact.getConfig().getCoreType());
				
				// we don't need to blacklist again as we will build upon the intermediary
				blacklist.clear();
				// make sure if we blacklisted a field that should be regenerated, it is blacklisted again for the output
				if (provider != null && provider.getConfig().getBlacklistedFields() != null) {
					blacklist.addAll(provider.getConfig().getBlacklistedFields());
				}
				updateOutput = addChild(artifact, entries, types, "updateOutput", updateIntermediaryInput, new ArrayList<String>(blacklist));
				
				blacklist.clear();
				blacklist.addAll(primary);
				// blacklist the fields we regenerate
				if (artifact.getConfig().getUpdateRegenerateFields() != null) {
					blacklist.addAll(artifact.getConfig().getUpdateRegenerateFields());
				}
				// generate the input
				updateInput = addChild(artifact, entries, types, "updateInput", updateOutput, blacklist);
				synchronize(updateInput, (ComplexType) artifact.getConfig().getCoreType());
			}
			DefinedStructure output = null;
			// if we have a provider with a create, add it
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getListService() != null) {
				List<String> blacklist = artifact.getConfig().getListBlacklistFields();
				// let's add to that
				blacklist = blacklist == null ? new ArrayList<String>() : new ArrayList<String>(blacklist);
				// generate the single output
				output = addChild(artifact, entries, types, "output", artifact.getConfig().getCoreType(), blacklist);
				synchronize(output, (ComplexType) artifact.getConfig().getCoreType());
				// we add the "extended" fields
				if (artifact.getConfig().getForeignFields() != null) {
					injectForeignFields(artifact.getConfig().getForeignFields(), artifact.getConfig().getCoreType(), artifact.getRepository(), output);
				}
				
				outputList = new DefinedStructure();
				outputList.setName(artifact.getConfig().getCoreType().getName() + "List");
				outputList.setId(types.getId() + ".outputList");
				outputList.add(new ComplexElementImpl("results", output, outputList, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
				outputList.add(new ComplexElementImpl("page", (ComplexType) BeanResolver.getInstance().resolve(Page.class), outputList, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				EAINode node = new EAINode();
				node.setArtifactClass(DefinedStructure.class);
				node.setArtifact(outputList);
				node.setLeaf(true);
				Entry childEntry = new MemoryEntry(artifact.getId(), types.getRepository(), types, node, outputList.getId(), "outputList");
				node.setEntry(childEntry);
				types.addChildren(childEntry);
				entries.add(childEntry);
			}
			
			// note that we can only add update services & list services if we have a primary key
			ModifiableEntry services = EAIRepositoryUtils.getParent(parent, "services", true);
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getCreateService() != null) {
				addChild(artifact, entries, services, "create", new CRUDService(artifact, services.getId() + ".create", CRUDType.CREATE, createInput, updateInput, outputList, updateIntermediaryInput, output, createOutput, updateOutput, null), "Create");
			}
			if (!primary.isEmpty() && artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getUpdateService() != null) {
				addChild(artifact, entries, services, "update", new CRUDService(artifact, services.getId() + ".update", CRUDType.UPDATE, createInput, updateInput, outputList, updateIntermediaryInput, output, createOutput, updateOutput, null), "Update");
			}
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getListService() != null) {
				addChild(artifact, entries, services, "list", new CRUDService(artifact, services.getId() + ".list", CRUDType.LIST, createInput, updateInput, outputList, updateIntermediaryInput, output, createOutput, updateOutput, artifact.asListAction()), "List");
				if (!primary.isEmpty()) {
					addChild(artifact, entries, services, "get", new CRUDService(artifact, services.getId() + ".get", CRUDType.GET, createInput, updateInput, outputList, updateIntermediaryInput, output, createOutput, updateOutput, artifact.asListAction()), "Get");
				}
			}
			if (!primary.isEmpty() && artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getDeleteService() != null) {
				addChild(artifact, entries, services, "delete", new CRUDService(artifact, services.getId() + ".delete", CRUDType.DELETE, createInput, updateInput, outputList, updateIntermediaryInput, output, createOutput, updateOutput, null), "Delete");
			}
			
			if (artifact.getConfig().getViews() != null) {
				for (CRUDView view : artifact.getConfig().getViews()) {
					switch(view.getType()) {
						case LIST:
							if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getListService() != null) {
								String name = view.getName().substring(0, 1).toUpperCase() + view.getName().substring(1);
								List<String> blacklist = view.getBlacklistFields();
								// let's add to that
								blacklist = blacklist == null ? new ArrayList<String>() : new ArrayList<String>(blacklist);
								// generate the single output
								output = addChild(artifact, entries, types, "output" + name, artifact.getConfig().getCoreType(), blacklist);
								synchronize(output, (ComplexType) artifact.getConfig().getCoreType());
								// we add the "extended" fields
								if (view.getForeignFields() != null) {
									injectForeignFields(view.getForeignFields(), artifact.getConfig().getCoreType(), artifact.getRepository(), output);
								}
								
								outputList = new DefinedStructure();
								outputList.setName(artifact.getConfig().getCoreType().getName() + name + "List");
								outputList.setId(types.getId() + ".output" + name + "List");
								outputList.add(new ComplexElementImpl("results", output, outputList, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
								outputList.add(new ComplexElementImpl("page", (ComplexType) BeanResolver.getInstance().resolve(Page.class), outputList, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
								EAINode node = new EAINode();
								node.setArtifactClass(DefinedStructure.class);
								node.setArtifact(outputList);
								node.setLeaf(true);
								Entry childEntry = new MemoryEntry(artifact.getId(), types.getRepository(), types, node, outputList.getId(), "output" + name + "List");
								node.setEntry(childEntry);
								types.addChildren(childEntry);
								entries.add(childEntry);
								
								addChild(artifact, entries, services, "list" + name, new CRUDService(artifact, services.getId() + ".list" + name, CRUDType.LIST, createInput, updateInput, outputList, updateIntermediaryInput, output, createOutput, updateOutput, view), "List " + name);
								if (!primary.isEmpty()) {
									addChild(artifact, entries, services, "get" + name, new CRUDService(artifact, services.getId() + ".get" + name, CRUDType.GET, createInput, updateInput, outputList, updateIntermediaryInput, output, createOutput, updateOutput, view), "Get " + name);
								}
							}
						break;
						default:
							throw new RuntimeException("View type not supported yet: " + view.getType());
					}
				}
			}
			
		}
		return entries;
	}

	public static List<String> injectForeignFields(List<ForeignNameField> foreignFields, DefinedType coreType, Repository repository, Structure output) {
		List<String> fields = new ArrayList<String>();
		for (ForeignNameField field : foreignFields) {
			// we need the same stats as the target (so same simple type, same optional-ness etc)
			String[] split = field.getForeignName().split(":");
			ComplexType current = ((ComplexType) coreType);
			Element<?> targetElement = null;
			// if any field along the way is optional, the entire end result is optional because we'll be doing an outer join
			boolean optional = false;
			for (int i = 0; i < split.length - 1; i++) {
				Element<?> element = current.get(split[i]);
				if (element != null) {
					Value<Integer> minOccurs = element.getProperty(MinOccursProperty.getInstance());
					if (minOccurs != null && minOccurs.getValue() != null && minOccurs.getValue() == 0) {
						optional = true;
					}
					Value<String> property = element.getProperty(ForeignKeyProperty.getInstance());
					if (property != null && property.getValue() != null) {
						String[] split2 = property.getValue().split(":");
						Artifact resolve = repository.resolve(split2[0]);
						if (resolve instanceof ComplexType) {
							targetElement = ((ComplexType) resolve).get(i == split.length - 2 ? split[i + 1] : split2[1]);
							current = (ComplexType) resolve;
						}
						else {
							targetElement = null;
							break;
						}
					}
				}
				else {
					targetElement = null;
					break;
				}
			}
			if (targetElement != null) {
				Element<?> clone = TypeBaseUtils.clone(targetElement, output);
				clone.setProperty(new ValueImpl<String>(ForeignNameProperty.getInstance(), field.getForeignName()));
				clone.setProperty(new ValueImpl<String>(NameProperty.getInstance(), field.getLocalName()));
				if (optional) {
					clone.setProperty(new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));
				}
				output.add(clone);
				fields.add(clone.getName());
			}
		}
		return fields;
	}
	
	private void synchronize(ModifiableComplexType to, ComplexType from) {
		to.setName(from.getName());
		for (Value<?> property : from.getProperties()) {
			if (CollectionNameProperty.getInstance().equals(property.getProperty())) {
				to.setProperty(property);
			}
			else if (DuplicateProperty.getInstance().equals(property.getProperty())) {
				to.setProperty(property);
			}
		}
	}
	
	public static List<String> getPrimary(ComplexType parent) {
		List<String> primaries = new ArrayList<String>();
		for (Element<?> element : TypeUtils.getAllChildren(parent)) {
			Value<Boolean> property = element.getProperty(PrimaryKeyProperty.getInstance());
			if (property != null && property.getValue() != null && property.getValue()) {
				primaries.add(element.getName());
			}
		}
		return primaries;
	}
	
	static List<String> getGenerated(ComplexType parent) {
		List<String> generated = new ArrayList<String>();
		for (Element<?> element : TypeUtils.getAllChildren(parent)) {
			Value<Boolean> property = element.getProperty(GeneratedProperty.getInstance());
			if (property != null && property.getValue() != null && property.getValue()) {
				generated.add(element.getName());
			}
		}
		return generated;
	}
	
	private void addChild(CRUDArtifact artifact, List<Entry> entries, ModifiableEntry services, String name, DefinedService service, String prettyName) {
		EAINode node = new EAINode();
		node.setArtifactClass(DefinedService.class);
		node.setArtifact(service);
		node.setLeaf(true);
		node.setName(prettyName);
//		if (service.getId().endsWith(".get")) {
//			node.setName("Get");
////			node.setDescription("Get a specific instance of this type by its identifier");
//		}
//		else if (service.getId().endsWith(".list")) {
//			node.setName("List");
////			node.setDescription("Search instances of this type by configurable filters");
//		}
//		else if (service.getId().endsWith(".update")) {
//			node.setName("Update");
////			node.setDescription("Update the data of an instance");
//		}
//		else if (service.getId().endsWith(".delete")) {
//			node.setName("Delete");
////			node.setDescription("Delete an instance of this type");
//		}
//		else if (service.getId().endsWith(".create")) {
//			node.setName("Create");
////			node.setDescription("Create a new instance of this type");
//		}
		Entry childEntry = new MemoryEntry(artifact.getId(), services.getRepository(), services, node, service.getId(), name);
		node.setEntry(childEntry);
		services.addChildren(childEntry);
		entries.add(childEntry);
	}
	
	private DefinedStructure addChild(CRUDArtifact artifact, List<Entry> entries, ModifiableEntry types, String name, DefinedType parent, List<String> fieldsToBlacklist) {
		EAINode node = new EAINode();
		node.setArtifactClass(DefinedStructure.class);
		DefinedStructure structure = new DefinedStructure();
		structure.setSuperType(parent);
		String id = types.getId() + "." + name;
		structure.setId(id);
		setBlacklist(fieldsToBlacklist, structure);
		node.setArtifact(structure);
		node.setLeaf(true);
		Entry childEntry = new MemoryEntry(artifact.getId(), types.getRepository(), types, node, id, name);
		node.setEntry(childEntry);
		types.addChildren(childEntry);
		entries.add(childEntry);
		return structure;
	}

	private void setBlacklist(List<String> fieldsToBlacklist, DefinedStructure structure) {
		if (fieldsToBlacklist != null && !fieldsToBlacklist.isEmpty()) {
			String blacklist = "";
			for (String single : fieldsToBlacklist) {
				if (!blacklist.isEmpty()) {
					blacklist += ",";
				}
				blacklist += single;
			}
			structure.setProperty(new ValueImpl<String>(RestrictProperty.getInstance(), blacklist));
		}
	}

	@Override
	public List<Entry> removeChildren(ModifiableEntry parent, CRUDArtifact artifact) throws IOException {
		List<Entry> entries = new ArrayList<Entry>();
		ModifiableEntry structures = EAIRepositoryUtils.getParent(parent, "types", true);
		removeRecursively(structures, entries);
		ModifiableEntry services = EAIRepositoryUtils.getParent(parent, "services", true);
		removeRecursively(services, entries);
		entries.add(services);
		entries.add(structures);
		parent.removeChildren("services", "types");
		return entries;
	}
	
	public static void removeRecursively(ModifiableEntry parent, List<Entry> entries) {
		List<String> toRemove = new ArrayList<String>();
		for (Entry child : parent) {
			if (child instanceof ModifiableEntry) {
				removeRecursively((ModifiableEntry) child, entries);
			}
			entries.add(child);
			toRemove.add(child.getName());
		}
		parent.removeChildren(toRemove.toArray(new String[toRemove.size()]));
	}

}
