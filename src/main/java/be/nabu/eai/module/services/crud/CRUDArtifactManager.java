package be.nabu.eai.module.services.crud;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.services.crud.CRUDService.CRUDType;
import be.nabu.eai.repository.EAINode;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.ArtifactRepositoryManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ModifiableEntry;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.eai.repository.resources.MemoryEntry;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.properties.GeneratedProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.PrimaryKeyProperty;
import be.nabu.libs.types.properties.RestrictProperty;
import be.nabu.libs.types.structure.DefinedStructure;

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
		// create the types
		if (artifact.getConfig().getCoreType() != null) {
			ModifiableEntry types = EAIRepositoryUtils.getParent(parent, "types", true);
			DefinedStructure createInput = null, updateInput = null, outputList = null, updateIntermediaryInput = null;
			// if we have a provider with a create, add it
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getCreateService() != null) {
				List<String> blacklist = artifact.getConfig().getCreateBlacklistFields();
				// let's add to that
				blacklist = blacklist == null ? new ArrayList<String>() : new ArrayList<String>(blacklist);
				blacklist.addAll(getPrimary((ComplexType) artifact.getConfig().getCoreType()));
				blacklist.addAll(getGenerated((ComplexType) artifact.getConfig().getCoreType()));
				// generate the input
				createInput = addChild(entries, types, "createInput", artifact.getConfig().getCoreType(), blacklist);
				synchronize(createInput, (ComplexType) artifact.getConfig().getCoreType());
			}
			// if we have a provider with a create, add it
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getUpdateService() != null) {
				List<String> blacklist = artifact.getConfig().getUpdateBlacklistFields();
				// let's add to that
				blacklist = blacklist == null ? new ArrayList<String>() : new ArrayList<String>(blacklist);
				
				// we create an intermediary update structure that contains all the fields you input as well as the fields we need to add to make it work (the primary key & any refreshable fields)
				blacklist.addAll(getGenerated((ComplexType) artifact.getConfig().getCoreType()));
				updateIntermediaryInput = addChild(entries, types, "updateIntermediaryInput", artifact.getConfig().getCoreType(), blacklist);
				synchronize(updateIntermediaryInput, (ComplexType) artifact.getConfig().getCoreType());
				
				// we don't need to blacklist again as we will build upon the intermediary
				blacklist.clear();
				blacklist.addAll(getPrimary((ComplexType) artifact.getConfig().getCoreType()));
				// add the fields we should regenerate (if any)
				if (artifact.getConfig().getUpdateRegenerateFields() != null) {
					blacklist.addAll(artifact.getConfig().getUpdateRegenerateFields());
				}
				// generate the input
				updateInput = addChild(entries, types, "updateInput", updateIntermediaryInput, blacklist);
				synchronize(updateInput, (ComplexType) artifact.getConfig().getCoreType());
			}
			// if we have a provider with a create, add it
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getListService() != null) {
				List<String> blacklist = artifact.getConfig().getListBlacklistFields();
				// let's add to that
				blacklist = blacklist == null ? new ArrayList<String>() : new ArrayList<String>(blacklist);
				// generate the input
				DefinedStructure output = addChild(entries, types, "output", artifact.getConfig().getCoreType(), blacklist);
				synchronize(output, (ComplexType) artifact.getConfig().getCoreType());
				
				outputList = new DefinedStructure();
				outputList.setName(artifact.getConfig().getCoreType().getName() + "List");
				outputList.setId(types.getId() + ".outputList");
				outputList.add(new ComplexElementImpl("results", output, outputList, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
				outputList.add(new ComplexElementImpl("page", (ComplexType) BeanResolver.getInstance().resolve(Page.class), outputList, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				EAINode node = new EAINode();
				node.setArtifactClass(DefinedStructure.class);
				node.setArtifact(outputList);
				node.setLeaf(true);
				Entry childEntry = new MemoryEntry(types.getRepository(), types, node, outputList.getId(), "outputList");
				node.setEntry(childEntry);
				types.addChildren(childEntry);
				entries.add(childEntry);
			}
			
			ModifiableEntry services = EAIRepositoryUtils.getParent(parent, "services", true);
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getCreateService() != null) {
				addChild(entries, services, "create", new CRUDService(artifact, services.getId() + ".create", CRUDType.CREATE, createInput, updateInput, outputList, updateIntermediaryInput));
			}
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getUpdateService() != null) {
				addChild(entries, services, "update", new CRUDService(artifact, services.getId() + ".update", CRUDType.UPDATE, createInput, updateInput, outputList, updateIntermediaryInput));
			}
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getListService() != null) {
				addChild(entries, services, "list", new CRUDService(artifact, services.getId() + ".list", CRUDType.LIST, createInput, updateInput, outputList, updateIntermediaryInput));
			}
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getDeleteService() != null) {
				addChild(entries, services, "delete", new CRUDService(artifact, services.getId() + ".delete", CRUDType.DELETE, createInput, updateInput, outputList, updateIntermediaryInput));
			}
		}
		return entries;
	}
	
	private void synchronize(ModifiableComplexType to, ComplexType from) {
		to.setName(from.getName());
		for (Value<?> property : from.getProperties()) {
			if (CollectionNameProperty.getInstance().equals(property.getProperty())) {
				to.setProperty(property);
			}
		}
	}
	
	static List<String> getPrimary(ComplexType parent) {
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
	
	private void addChild(List<Entry> entries, ModifiableEntry services, String name, DefinedService service) {
		EAINode node = new EAINode();
		node.setArtifactClass(DefinedService.class);
		node.setArtifact(service);
		node.setLeaf(true);
		Entry childEntry = new MemoryEntry(services.getRepository(), services, node, service.getId(), name);
		node.setEntry(childEntry);
		services.addChildren(childEntry);
		entries.add(childEntry);
	}
	
	private DefinedStructure addChild(List<Entry> entries, ModifiableEntry types, String name, DefinedType parent, List<String> fieldsToBlacklist) {
		EAINode node = new EAINode();
		node.setArtifactClass(DefinedStructure.class);
		DefinedStructure structure = new DefinedStructure();
		structure.setSuperType(parent);
		String id = types.getId() + "." + name;
		structure.setId(id);
		setBlacklist(fieldsToBlacklist, structure);
		node.setArtifact(structure);
		node.setLeaf(true);
		Entry childEntry = new MemoryEntry(types.getRepository(), types, node, id, name);
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
