package be.nabu.eai.module.services.crud.collection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.CollectionAction;
import be.nabu.eai.developer.CollectionActionImpl;
import be.nabu.eai.developer.api.CollectionManager;
import be.nabu.eai.developer.api.CollectionManagerFactory;
import be.nabu.eai.developer.api.EntryAcceptor;
import be.nabu.eai.developer.collection.ApplicationManager;
import be.nabu.eai.developer.collection.EAICollectionUtils;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.module.services.crud.CRUDArtifact;
import be.nabu.eai.module.services.crud.CRUDArtifactManager;
import be.nabu.eai.module.services.crud.provider.CRUDProviderArtifact;
import be.nabu.eai.repository.CollectionImpl;
import be.nabu.eai.repository.api.Collection;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.DataSourceProviderArtifact;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.SynchronizableTypeRegistry;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.api.TypeRegistry;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class CRUDCollectionManagerFactory implements CollectionManagerFactory {

	@Override
	public CollectionManager getCollectionManager(Entry entry) {
		if (entry.isNode() && CRUDArtifact.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
			return new CRUDCollectionManager(entry);
		}
		return null;
	}
	
	public static class ComboItem {
		private String display;
		private Object content;
		public ComboItem(String display, Object content) {
			this.display = display;
			this.content = content;
		}
		public String getDisplay() {
			return display;
		}
		public void setDisplay(String display) {
			this.display = display;
		}
		public Object getContent() {
			return content;
		}
		public void setContent(Object content) {
			this.content = content;
		}
		@Override
		public String toString() {
			return display;
		}
	}

	@Override
	public List<CollectionAction> getActionsFor(Entry entry) {
		List<CollectionAction> actions = new ArrayList<CollectionAction>();
		// if it is a valid application, we want to be able to add to it
		if (MainController.getInstance().newCollectionManager(entry) instanceof ApplicationManager) {
			actions.add(new CollectionActionImpl(EAICollectionUtils.newActionTile("crud-big.png", "Add CRUD", "Create, read, update and delete database records."),	new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					Map<String, List<TypeRegistry>> registries = new HashMap<String, List<TypeRegistry>>();
					ComboBox<ComboItem> databases = new ComboBox<ComboItem>();
					Entry project = EAICollectionUtils.getProject(entry);
					for (DataSourceProviderArtifact database : project.getRepository().getArtifacts(DataSourceProviderArtifact.class)) {
						// in this project
						if (database.getId().startsWith(project.getId() + ".")) {
							// we expect a data model to be nearby, otherwise we can't really do much
							Entry databaseEntry = entry.getRepository().getEntry(database.getId());
							boolean hasRegistrySibling = false;
							for (Entry child : databaseEntry.getParent()) {
								try {
									if (child.isNode() && TypeRegistry.class.isAssignableFrom(child.getNode().getArtifactClass())) {
										hasRegistrySibling = true;
										if (!registries.containsKey(database.getId())) {
											registries.put(database.getId(), new ArrayList<TypeRegistry>());
										}
										registries.get(database.getId()).add((TypeRegistry) child.getNode().getArtifact());
										hasRegistrySibling = true;
									}
								}
								catch (Exception e) {
									MainController.getInstance().notify(e);
								}
							}
							if (hasRegistrySibling) {
								databases.getItems().add(new ComboItem(EAICollectionUtils.getPrettyName(databaseEntry.getParent()), databaseEntry));
							}
						}
					}
					
					Button create = new Button("Create");
					create.setDisable(true);
					create.getStyleClass().add("primary");
					
					Button cancel = new Button("Cancel");
												
					HBox buttons = new HBox();
					buttons.getStyleClass().add("buttons");
					buttons.getChildren().addAll(create, cancel);
					
					VBox root = new VBox();
					Stage stage = EAIDeveloperUtils.buildPopup("Create CRUD", root, MainController.getInstance().getActiveStage(), StageStyle.DECORATED, false);
					
					// we want to be able to add multiple CRUD at once, we use checkboxes
					// if you already have a CRUD with the type name, we assume you generated it properly and we don't offer it as a possibility anymore
					VBox options = new VBox();
					Map<String, CheckBox> boxes = new HashMap<String, CheckBox>();
					
					databases.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<ComboItem>() {
						@Override
						public void changed(ObservableValue<? extends ComboItem> arg0, ComboItem arg1, ComboItem arg2) {
							options.getChildren().clear();
							if (arg2 != null) {
								List<String> alreadyTaken = new ArrayList<String>();
								// first we discover what we already have
								Entry databaseEntry = (Entry) arg2.getContent();
								// we get the name of the parent folder, for a database we assume that is the name of the entire thing (like main)
								String databaseName = databaseEntry.getParent().getName();
								// we get the crud folder
								Entry crud = entry.getChild("crud");
								// we might not yet have any crud at all
								if (crud != null) {
									// we might not have any crud for this database yet
									Entry crudChild = crud.getChild(databaseName);
									if (crudChild != null) {
										for (Entry child : crudChild) {
											// if we have a crud, add the type id
											if (child.isNode() && CRUDArtifact.class.isAssignableFrom(child.getNode().getArtifactClass())) {
												try {
													DefinedType coreType = ((CRUDArtifact) child.getNode().getArtifact()).getConfig().getCoreType();
													if (coreType != null) {
														alreadyTaken.add(coreType.getId());
													}
												}
												catch (Exception e) {
													MainController.getInstance().notify(e);
												}
											}
										}
									}
								}
								boxes.clear();
								// now we loop over the available type in the available registries and suggest those that have not been crudded yet!
								for (TypeRegistry typeRegistry : registries.get(databaseEntry.getId())) {
									for (String namespace : typeRegistry.getNamespaces()) {
										for (ComplexType type : typeRegistry.getComplexTypes(namespace)) {
											// we only bother with defined types (for now?)
											if (type instanceof DefinedType) {
												// if we can synchronize it, do it!
												if (!(typeRegistry instanceof SynchronizableTypeRegistry) || ((SynchronizableTypeRegistry) typeRegistry).isSynchronizable(type)) {
													// it musn't already exist
													if (!alreadyTaken.contains(((DefinedType) type).getId())) {
														CheckBox checkBox = new CheckBox(((DefinedType) type).getId());
														boxes.put(((DefinedType) type).getId(), checkBox);
														options.getChildren().add(checkBox);
													}
												}
											}
										}
									}
								}
								if (boxes.isEmpty()) {
									Label label = new Label("You already have a CRUD artifact for every available type");
									label.getStyleClass().add("p");
									options.getChildren().add(label);
									create.setDisable(true);
								}
								else {
									create.setDisable(false);
								}
							}
							stage.sizeToScene();
						}
					});
					
					root.getStyleClass().add("popup-form");
					Label label = new Label("Create CRUD Artifact");
					label.getStyleClass().add("h1");
					root.getChildren().addAll(label);
					
					if (databases.getItems().isEmpty()) {
						Label noDatabase = new Label("You don't have a database yet in this project, add one first");
						noDatabase.getStyleClass().add("p");
						root.getChildren().add(noDatabase);
					}
					else {
						root.getChildren().add(EAIDeveloperUtils.newHBox("Database", databases));
					}
					
					ScrollPane scroll = new ScrollPane();
					scroll.setContent(options);
					scroll.setMaxHeight(400);
					scroll.setFitToWidth(true);
					scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
					
					root.getChildren().addAll(scroll, buttons);
					
					cancel.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							stage.hide();
						}
					});
					
					create.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							Entry databaseEntry = (Entry) databases.getSelectionModel().getSelectedItem().getContent();
							try {
								Entry crudDatabaseEntry = getCrudDatabaseEntry((RepositoryEntry) entry, databaseEntry);
								
								for (Map.Entry<String, CheckBox> box : boxes.entrySet()) {
									if (box.getValue().isSelected()) {
										String typeId = box.getKey();
										String name = typeId.replaceAll("^.*\\.", "");
										String prettyName = EAICollectionUtils.getPrettyName((Type) entry.getRepository().resolve(typeId));
										int counter = 1;
										String finalName = name;
										while (crudDatabaseEntry.getChild(finalName) != null) {
											finalName = name + counter++;
										}
										RepositoryEntry crudEntry = ((RepositoryEntry) crudDatabaseEntry).createNode(finalName, new CRUDArtifactManager(), true);
										CRUDArtifact artifact = new CRUDArtifact(crudEntry.getId(), crudEntry.getContainer(), crudEntry.getRepository());
										artifact.getConfig().setCoreType((DefinedType) entry.getRepository().resolve(typeId));
										artifact.getConfig().setProvider((CRUDProviderArtifact) entry.getRepository().resolve("nabu.services.crud.provider.basic.provider"));
										new CRUDArtifactManager().save(crudEntry, artifact);
										if (!prettyName.equals(name)) {
											crudEntry.getNode().setName(prettyName);
											crudEntry.saveNode();
										}
										EAIDeveloperUtils.created(crudEntry.getId());
										// we hard reload the crud entry to make sure we see the new services
										Platform.runLater(new Runnable() {
											@Override
											public void run() {
												EAIDeveloperUtils.reload(crudEntry.getId());
											}
										});
									}
								}
							}
							catch (Exception e) {
								MainController.getInstance().notify(e);
							}
							stage.hide();
						}
					});
					stage.show();
					stage.sizeToScene();
				}
			}, new EntryAcceptor() {
				@Override
				public boolean accept(Entry entry) {
					Collection collection = entry.getCollection();
					return collection != null && "folder".equals(collection.getType()) && "crud".equals(collection.getSubType());
				}
			}));
		}
		return actions;
	}
	
	// the actual database entry?
	private Entry getCrudDatabaseEntry(RepositoryEntry application, Entry databaseEntry) throws IOException {
		Entry crudEntry = getCrudEntry(application);
		Entry parent = databaseEntry.getParent();
		String databaseName = parent.getName();
		
		Entry child = EAIDeveloperUtils.mkdir((RepositoryEntry) crudEntry, databaseName);
		if (!child.isCollection()) {
			Collection parentCollection = parent.getCollection();
			CollectionImpl collection = new CollectionImpl();
			collection.setType("folder");
			if (parentCollection != null) {
				collection.setName(parentCollection.getName());
			}
			collection.setSmallIcon("crud.png");
			collection.setMediumIcon("crud-medium.png");
			collection.setLargeIcon("crud-big.png");
			collection.setSubType("crud");
			((RepositoryEntry) child).setCollection(collection);
			((RepositoryEntry) child).saveCollection();
		}
		return child;
	}
	
	private Entry getCrudEntry(RepositoryEntry application) throws IOException {
		Entry child = EAIDeveloperUtils.mkdir(application, "crud");
		return child;
	}
}
