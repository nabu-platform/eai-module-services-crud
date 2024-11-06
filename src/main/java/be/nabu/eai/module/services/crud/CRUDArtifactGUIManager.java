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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.developer.ComplexContentEditor;
import be.nabu.eai.developer.ComplexContentEditor.ValueWrapper;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.impl.CustomTooltip;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.developer.util.EAIDeveloperUtils.PropertiesHandler;
import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;
import be.nabu.eai.module.services.crud.CRUDService.CRUDType;
import be.nabu.eai.module.services.crud.api.CRUDListAction;
import be.nabu.eai.module.services.crud.provider.CRUDProviderArtifact;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.services.jdbc.JDBCUtils;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.CollectionCrudProviderProperty;
import be.nabu.libs.types.properties.ForeignKeyProperty;
import be.nabu.libs.validator.api.Validation;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

// for list, we could allow configuring a custom jdbc service? if relations get too complex, it might be necessary?
public class CRUDArtifactGUIManager extends BaseJAXBGUIManager<CRUDConfiguration, CRUDArtifact> {

	private VBox tableContainer;
	private CRUDArtifact instance;
	
	private static ObservableList<String> blacklistClipboard = FXCollections.observableArrayList();

	static {
		URL resource = CRUDArtifactGUIManager.class.getClassLoader().getResource("crud.css");
		if (resource != null) {
			MainController.registerStyleSheet(resource.toExternalForm());
		}
	}
	
	public CRUDArtifactGUIManager() {
		super("CRUD", CRUDArtifact.class, new CRUDArtifactManager(), CRUDConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return Arrays.asList(new SimpleProperty<DefinedType>("Type", DefinedType.class, true));
	}

	@Override
	protected CRUDArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		CRUDArtifact artifact = new CRUDArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
		if (values != null && values.length > 0) {
			for (Value<?> value : values) {
				if (value.getValue() instanceof DefinedType) {
					artifact.getConfig().setCoreType((DefinedType) value.getValue());
				}
			}
		}
		artifact.getConfig().setProvider((CRUDProviderArtifact) entry.getRepository().resolve("nabu.services.crud.provider.basic.provider"));
		List<String> asList = Arrays.asList("$user");
		artifact.getConfig().setListRole(new ArrayList<String>(asList));
		artifact.getConfig().setCreateRole(new ArrayList<String>(asList));
		artifact.getConfig().setUpdateRole(new ArrayList<String>(asList));
		artifact.getConfig().setDeleteRole(new ArrayList<String>(asList));
		artifact.getConfig().setUseListOutputForCreate(true);
		artifact.getConfig().setUseListOutputForUpdate(true);
		if (artifact.getConfig().getCoreType() == null) {
			throw new IllegalStateException("You need to define a type");
		}
		String crudProvider = ValueUtils.getValue(CollectionCrudProviderProperty.getInstance(), artifact.getConfig().getCoreType().getProperties());
		if (crudProvider == null) {
			crudProvider = "default";
		}
		for (CRUDProviderArtifact potentialProvider : entry.getRepository().getArtifacts(CRUDProviderArtifact.class)) {
			// can map it on id?
			if (potentialProvider.getId().equals(crudProvider)) {
				artifact.getConfig().setProvider(potentialProvider);
				break;
			}
			Map<String, String> nodeProperties = entry.getRepository().getNode(potentialProvider.getId()).getProperties();
			if (nodeProperties != null && nodeProperties.containsKey("crudProvider")) {
				String providerName = nodeProperties.get("crudProvider");
				if (providerName != null && providerName.equals(crudProvider)) {
					artifact.getConfig().setProvider(potentialProvider);
					break;
				}
			}
		}
		return artifact;
	}

	private void redraw(CRUDArtifact instance, Pane pane) {
		pane.getChildren().removeAll();
		display(instance, pane);
	}
	
	@Override
	protected List<String> getBlacklistedProperties() {
		return Arrays.asList("createBlacklistFields", "updateBlacklistFields", "listBlacklistFields", "updateRegenerateFields",
				"securityContextField", "parentField", "filters", "foreignFields", "coreType", "views");
	}

	@Override
	protected void display(CRUDArtifact instance, Pane pane) {
		this.instance = instance;
		SplitPane split = new SplitPane();
		// give most screen real estate to the settings
		split.setDividerPositions(0.65);
		
		AnchorPane.setBottomAnchor(split, 0d);
		AnchorPane.setTopAnchor(split, 0d);
		AnchorPane.setLeftAnchor(split, 0d);
		AnchorPane.setRightAnchor(split, 0d);
		
		ScrollPane left = new ScrollPane();
		left.setFitToWidth(true);
		left.setFitToHeight(true);
		
		ScrollPane right = new ScrollPane();
		right.setFitToWidth(true);
		right.setFitToHeight(true);
		
		split.getItems().addAll(left, right);
		
		
		// for list, create & update, we want to select the relevant fields to expose to the end user
		// for list we want to additionally set filters (both exposed as input or hardcoded)
		
		// we want some generic settings like which field to use for contextual security
		// for create, we may want to indicate like a parent field? which is used for security and to link?
		// we basically tell the service which field it is, at which point it will be added to the path and automatically mapped?
	
		Accordion accordion = new Accordion();
		
		VBox box = new VBox();
		box.setFillWidth(true);
		showProperties(instance, box, false);
		populateGeneral(instance, box);
		box.getStyleClass().add("configuration-pane");
		box.getStyleClass().add("configuration-pane-basic");
		// the default settings should be good enough?
		TitledPane general = new TitledPane("Advanced", box);
		
		AnchorPane listPane = new AnchorPane();
		populateList(instance, instance.asListAction(), listPane);
		listPane.getStyleClass().add("configuration-pane");
		listPane.getStyleClass().add("configuration-pane-basic");
		TitledPane list = new TitledPane("List", listPane);
		accordion.getPanes().add(list);
		
		if (instance.getConfig().getViews() != null) {
			for (CRUDView view : instance.getConfig().getViews()) {
				if (CRUDType.LIST.equals(view.getType())) {
					AnchorPane viewPane = new AnchorPane();
					populateList(instance, view, viewPane);
					viewPane.getStyleClass().add("configuration-pane");
					viewPane.getStyleClass().add("configuration-pane-basic");
					TitledPane viewList = new TitledPane("List " + view.getName(), viewPane);
					accordion.getPanes().add(viewList);
				}
			}
		}
		
		AnchorPane createPane = new AnchorPane();
		populateCreate(instance, createPane);
		listPane.getStyleClass().add("configuration-pane");
		listPane.getStyleClass().add("configuration-pane-basic");
		TitledPane create = new TitledPane("Create", createPane);
		accordion.getPanes().add(create);
		
		AnchorPane updatePane = new AnchorPane();
		populateUpdate(instance, updatePane);
		listPane.getStyleClass().add("configuration-pane");
		listPane.getStyleClass().add("configuration-pane-basic");
		TitledPane update = new TitledPane("Update", updatePane);
		accordion.getPanes().add(update);
		
		// if we have a configuration type, add a pane for that
		if (instance.getConfig().getProvider() != null && instance.getConfig().getProvider().getConfig().getConfigurationType() != null) {
			AnchorPane configurationPane = new AnchorPane();
			System.out.println("trying to get configuration for " + instance.getConfig().getProvider().getConfig().getConfigurationType()  + " = > " + instance.getProviderConfiguration() + " id " + instance.getId());
			Tree<ValueWrapper> tree = new ComplexContentEditor(instance.getProviderConfiguration(), true, instance.getRepository()).getTree();
			AnchorPane.setBottomAnchor(tree, 0d);
			AnchorPane.setLeftAnchor(tree, 0d);
			AnchorPane.setRightAnchor(tree, 0d);
			AnchorPane.setTopAnchor(tree, 0d);
			configurationPane.getChildren().add(tree);
			listPane.getStyleClass().add("configuration-pane");
			listPane.getStyleClass().add("configuration-pane-basic");
			TitledPane configurationTitledPane = new TitledPane("Configuration", configurationPane);
			accordion.getPanes().add(configurationTitledPane);
		}
		
		accordion.getPanes().add(general);
		
		accordion.setExpandedPane(list);
		
		left.setContent(accordion);
//		tableContainer = new VBox();
//		right.setContent(tableContainer);
//		pane.getChildren().add(split);
//		maximize(accordion);
//		tableContainer.setPadding(new Insets(5));
//		displayContent(tableContainer, instance);
		maximize(left);
		pane.getChildren().add(left);
	}
	
	@Override
	protected BaseArtifactGUIInstance<CRUDArtifact> newGUIInstance(Entry entry) {
		return new BaseArtifactGUIInstance<CRUDArtifact>(this, entry) {
			@Override
			public List<Validation<?>> save() throws IOException {
				List<Validation<?>> save = super.save();
				MainController.getInstance().submitTask("Refresh data", "Refreshing data for " + entry.getId(), new Runnable() {
					@Override
					public void run() {
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								displayContent(tableContainer, instance);
							}
						});
					}
				}, 1000);
				return save;
			}
		};
	}
	
	private void displayContent(VBox container, CRUDArtifact artifact) {
		container.getChildren().clear();
		Artifact resolve = artifact.getRepository().resolve(artifact.getId() + ".services.list");
		if (resolve instanceof DefinedService) {
			// TODO: add filters!
			try {
				TilePane filters = new TilePane();

				// we need to retain state for the filters, we also need this for insight!!! make it a generic something?
				// if you change a value, we need a debounce or a specific button to search (which is annoying)
//				for (CRUDFilter filter : artifact.getConfig().getFilters()) {
//					
//				}
				if (!filters.getChildren().isEmpty()) {
					VBox.setMargin(filters, new Insets(5));
					container.getChildren().add(filters);
				}
				
				DefinedService service = (DefinedService) resolve;
				ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
				// TODO: make this configurable by the user, then you can use it to for example get a full export!
				input.set("limit", 100);
				Future<ServiceResult> run = EAIResourceRepository.getInstance().getServiceRunner().run(service, EAIResourceRepository.getInstance().newExecutionContext(SystemPrincipal.ROOT), input);
				ServiceResult serviceResult = run.get();
				if (serviceResult.getException() != null) {
					throw serviceResult.getException();
				}
				ComplexContent output = serviceResult.getOutput();
				if (output != null) {
					VBox content = new VBox();
					MainController.getInstance().showContent(content, output, null);
					container.getChildren().add(content);
				}
			}
			catch (Exception e) {
				MainController.getInstance().notify(e);
			}
		}
	}

	private void maximize(Node node) {
		AnchorPane.setBottomAnchor(node, 0d);
		AnchorPane.setRightAnchor(node, 0d);
		AnchorPane.setTopAnchor(node, 0d);
		AnchorPane.setLeftAnchor(node, 0d);
	}
	
	private void populateGeneral(CRUDArtifact instance, Pane general) {
		VBox main = new VBox();
		main.setFillWidth(true);
		
		// select the security context field
		ComboBox<String> securityContextField = newFieldCombo(instance.getConfig().getForeignFields(), instance.getConfig().getCoreType(), true);
		securityContextField.getSelectionModel().select(instance.getConfig().getSecurityContextField());
		securityContextField.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				instance.getConfig().setSecurityContextField(arg2 == null || arg2.trim().isEmpty() ? null : arg2);
				MainController.getInstance().setChanged();
			}
		});
		HBox securityField = createField(securityContextField, "Security Context Field", "Configure the security context field for this type.");
		HBox.setMargin(securityContextField, new Insets(0, 0, 0, 7));
		HBox.setHgrow(securityContextField, Priority.ALWAYS);
		main.getChildren().add(securityField);
		
		// list and create have security field of parent
		// for the list, it has to be one of the filters, for create there has to be a field to store it in
		// update & delete have security field of child (by default the primary key, but you can for instance also set it on the parent)
		
		// allow for a fixed order by or by input (can be combined?)
		
		general.getChildren().add(main);
		maximize(main);
	}

	private HBox createField(ComboBox<String> node, String title, String tooltip) {
		HBox hbox = new HBox();
		hbox.setPadding(new Insets(10, 0, 10, 0));
		Label label = new Label(title + ":");
		label.setPrefWidth(160);
		label.setWrapText(true);
		label.setAlignment(Pos.CENTER_LEFT);
		label.setPadding(new Insets(4, 10, 0, 0));
		HBox.setHgrow(label, Priority.SOMETIMES);
		hbox.getChildren().addAll(label, node);
		HBox.setHgrow(node, Priority.ALWAYS);
		MainController.getInstance().attachTooltip(label, tooltip);
		label.setStyle("-fx-text-fill: #666666");
		return hbox;
	}

	private void populateCreate(CRUDArtifact instance, Pane pane) {
		VBox main = new VBox();
		main.setPadding(new Insets(10));
		pane.getChildren().add(main);
		
		Label label = new Label("Choose the fields you want to blacklist from the create input:");
		VBox.setMargin(label, new Insets(10, 0, 10, 0));
		main.getChildren().add(label);
		
		List<String> list = new ArrayList<String>();
		list.addAll(CRUDArtifactManager.getPrimary((ComplexType) instance.getConfig().getCoreType()));
		list.addAll(CRUDArtifactManager.getGenerated((ComplexType) instance.getConfig().getCoreType()));
		
		if (instance.getConfig().getCreateBlacklistFields() == null) {
			instance.getConfig().setCreateBlacklistFields(new ArrayList<String>());
		}
		populateChecklist(instance, main, instance.getConfig().getCreateBlacklistFields(), list, true, true); // buildCopyButton("Copy from List", instance.getConfig().getListBlacklistFields(), instance.getConfig().getCreateBlacklistFields()
		
		maximize(main);
	}
	
	private void populateUpdate(CRUDArtifact instance, Pane pane) {
		VBox main = new VBox();
		main.setPadding(new Insets(10));
		pane.getChildren().add(main);
		
		List<String> list = new ArrayList<String>();
		list.addAll(CRUDArtifactManager.getPrimary((ComplexType) instance.getConfig().getCoreType()));
		list.addAll(CRUDArtifactManager.getGenerated((ComplexType) instance.getConfig().getCoreType()));
		
		Label label = new Label("Choose the fields you want to blacklist from the update input:");
		VBox.setMargin(label, new Insets(10, 0, 10, 0));
		main.getChildren().add(label);
		if (instance.getConfig().getUpdateBlacklistFields() == null) {
			instance.getConfig().setUpdateBlacklistFields(new ArrayList<String>());
		}
		populateChecklist(instance, main, instance.getConfig().getUpdateBlacklistFields(), list, true, true);
		
		boolean foundAny = false;
		label = new Label("Choose the fields you want to regenerate:");
		VBox.setMargin(label, new Insets(10, 0, 10, 0));
		List<String> ignore = new ArrayList<String>();
		for (Element<?> element : TypeUtils.getAllChildren((ComplexType) instance.getConfig().getCoreType())) {
			if (!(element.getType() instanceof SimpleType)) {
				continue;
			}
			else if (!Date.class.isAssignableFrom(((SimpleType<?>) element.getType()).getInstanceClass())) {
				ignore.add(element.getName());
			}
			else {
				foundAny = true;
			}
		}
		if (foundAny) {
			if (instance.getConfig().getUpdateRegenerateFields() == null) {
				instance.getConfig().setUpdateRegenerateFields(new ArrayList<String>());
			}
			main.getChildren().add(label);
			populateChecklist(instance, main, instance.getConfig().getUpdateRegenerateFields(), ignore, true, false);
		}
		maximize(main);
	}
	
	private static boolean operatorIsInput(String operator) {
		// without an operator, we assume it is an input
		if (operator == null) {
			return true;
		}
		else {
			return CRUDService.inputOperators.contains(operator);
		}
	}
	
	private void populateList(CRUDArtifact artifact, CRUDListAction instance, Pane list) {
		VBox main = new VBox();
		main.setPadding(new Insets(10));
		list.getChildren().add(main);
		
		if (instance.getFilters() == null) {
			instance.setFilters(new ArrayList<CRUDFilter>());
		}
		
		if (instance.getForeignFields() == null) {
			instance.setForeignFields(new ArrayList<ForeignNameField>());
		}
		
		Artifact resolve = artifact.getRepository().resolve("nabu.misc.broadcast.Services.fire");
		CheckBox broadcastUpdate = resolve == null ? null : new CheckBox("On update");
		// set the boolean now already, otherwise we will always start off unchecked
		if (broadcastUpdate != null) {
			broadcastUpdate.setSelected(instance.isBroadcastUpdate());
		}
		
		Label label;
		drawFilters(instance.getForeignFields(), artifact.getConfig().getCoreType(), instance.getFilters(), main, new Redrawer() {
			@Override
			public void redraw() {
				list.getChildren().clear();
				populateList(artifact, instance, list);				
			}
		}, true, broadcastUpdate);

		VBox fields = new VBox();
		fields.getStyleClass().addAll("section");
		main.getChildren().add(fields);
		label = new Label("Fields");
		label.getStyleClass().add("h1");
		fields.getChildren().add(label);
		
		// then checkboxes to choose the fields you want to blacklist
		label = new Label("Choose the fields you want to blacklist from the resultset:");
		label.getStyleClass().add("p");
		fields.getChildren().add(label);
		if (instance.getBlacklistFields() == null) {
			instance.setBlacklistFields(new ArrayList<String>());
		}
		populateChecklist(artifact, fields, instance.getBlacklistFields(), new ArrayList<String>(), false, true);
		
		VBox foreign = new VBox();
		foreign.getStyleClass().add("section");
		drawForeignNameFields(instance.getForeignFields(), artifact.getConfig().getCoreType(), artifact.getRepository(), foreign);
		if (!foreign.getChildren().isEmpty()) {
			main.getChildren().add(foreign);
		}
		
		VBox miscBox = new VBox();
		miscBox.getStyleClass().addAll("section");
		label = new Label("Miscellaneous");
		label.getStyleClass().add("h1");
		miscBox.getChildren().add(label);
		
		// we can only broadcast if we have the service, don't offer the option otherwise
		if (resolve != null) {
			VBox broadcast = new VBox();
			broadcast.getStyleClass().addAll("section");
			label = new Label("Broadcast");
			label.getStyleClass().add("h1");
			broadcast.getChildren().add(label);
			label = new Label("Check if and when you want to broadcast:");
			label.getStyleClass().add("p");
			broadcast.getChildren().add(label);
			
			CheckBox box = new CheckBox("On create");
			box.setSelected(instance.isBroadcastCreate());
			box.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
					if (instance instanceof CRUDView) {
						((CRUDView) instance).setBroadcastCreate(arg2 != null && arg2);
					}
					else {
						artifact.getConfig().setBroadcastCreate(arg2 != null && arg2);
					}
					MainController.getInstance().setChanged();
				}
			});
			VBox checkboxes = new VBox();
			checkboxes.getChildren().add(box);
			VBox.setMargin(box, new Insets(3, 20, 0, 0));
			
			broadcastUpdate.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
					if (instance instanceof CRUDView) {
						((CRUDView) instance).setBroadcastUpdate(arg2 != null && arg2);
					}
					else {
						artifact.getConfig().setBroadcastUpdate(arg2 != null && arg2);
					}
					MainController.getInstance().setChanged();
				}
			});
			checkboxes.getChildren().add(broadcastUpdate);
			VBox.setMargin(broadcastUpdate, new Insets(3, 20, 0, 0));
			
			broadcast.getChildren().add(checkboxes);
			main.getChildren().add(broadcast);
		}
	}

	public static interface Redrawer {
		public void redraw();
	}
	
	public static void drawFilters(List<ForeignNameField> foreignFields, DefinedType coreType, List<CRUDFilter> crudFilters, VBox main, Redrawer redrawer, boolean allowAddAll, CheckBox broadcastUpdate) {
		VBox filters = new VBox();
		main.getChildren().add(filters);
		filters.getStyleClass().addAll("section", "block");
		Label label = new Label("Filters");
		label.getStyleClass().addAll("section-title", "h1");
		filters.getChildren().add(label);
		
		Label nodata = new Label("You have not yet added any filters. Click the add filter button below to get started.");
		nodata.getStyleClass().add("p");
		if (crudFilters.isEmpty()) {
			filters.getChildren().add(nodata);
		}
		
		// if you want to hardcode values, add it to the operator (for now)
		// first we define the filters
		for (CRUDFilter filter : crudFilters) {
			HBox filterBox = new HBox();
			filterBox.setAlignment(Pos.CENTER_LEFT);
			ComboBox<String> mainOperator = new ComboBox<String>();
			mainOperator.getItems().addAll("and", "or");
			if (filter.isOr()) {
				mainOperator.getSelectionModel().select("or");
			}
			else {
				mainOperator.getSelectionModel().select("and");
			}
			mainOperator.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					filter.setOr(arg2 != null && arg2.equals("or"));
					MainController.getInstance().setChanged();
				}
			});
			TextField alias = new TextField();
			alias.setPromptText("Alias");
			alias.setText(filter.getAlias());
			alias.textProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					filter.setAlias(arg2 == null || arg2.trim().isEmpty() ? null : arg2);
					MainController.getInstance().setChanged();
				}
			});
			ComboBox<String> field = newFieldCombo(foreignFields, coreType, true);
			// if you have selected something that no longer exists
			// we shall add an invalid class
			if (!field.getItems().contains(filter.getKey())) {
				field.getStyleClass().add("invalid");
			}
			field.getSelectionModel().select(filter.getKey());
			field.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					filter.setKey(arg2 == null || arg2.trim().isEmpty() ? null : arg2);
					MainController.getInstance().setChanged();
					// this assumes you can not select an invalid filter
					field.getStyleClass().remove("invalid");
				}
			});
			CheckBox input = new CheckBox();
			if (operatorIsInput(filter.getOperator())) {
				input.setDisable(true);
				filter.setInput(true);
				input.setVisible(false);
				input.setManaged(false);
			}
			
			CheckBox vary = new CheckBox();
			new CustomTooltip("Do you want to allow subscription of updated values that do not match the filter?").install(vary);
			vary.setSelected(filter.isVary());
			vary.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
					if (arg2 != null && arg2) {
						filter.setVary(true);
					}
					else {
						filter.setVary(false);
					}
					MainController.getInstance().setChanged();
				}
			});
			// if we don't have broadcasting or don't broadcast updates, the vary option is not relevant
			if (broadcastUpdate == null || !broadcastUpdate.isSelected()) {
				vary.setDisable(true);
				filter.setVary(false);
				vary.setSelected(false);
				vary.setVisible(false);
				vary.setManaged(false);
			}
			if (broadcastUpdate != null) {
				broadcastUpdate.selectedProperty().addListener(new ChangeListener<Boolean>() {
					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
						if (arg2 != null && arg2) {
							vary.setDisable(false);
							vary.setVisible(true);
							vary.setManaged(true);
						}
						else {
							vary.setDisable(true);
							filter.setVary(false);
							vary.setSelected(false);
							vary.setVisible(false);
							vary.setManaged(false);
						}
					}
				});
			}
			
			ComboBox<String> operator = new ComboBox<String>();
			operator.getStyleClass().add("smaller");
			HBox.setHgrow(operator, Priority.ALWAYS);
			operator.setEditable(true);
			operator.setValue(filter.getOperator());
			operator.getItems().addAll(CRUDService.operators);
			operator.valueProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					filter.setOperator(arg2 == null || arg2.trim().isEmpty() ? null : arg2);
					if (operatorIsInput(filter.getOperator())) {
						input.setDisable(true);
						filter.setInput(true);
						input.setSelected(true);
						input.setVisible(false);
						input.setManaged(false);
					}
					// if we switched away from an input operator, unset all
					else if (input.isDisabled()) {
						input.setDisable(false);
						filter.setInput(false);
						input.setSelected(false);
						input.setVisible(true);
						input.setManaged(true);
					}
					MainController.getInstance().setChanged();
				}
			});
			input.setSelected(filter.isInput());
			input.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
					filter.setInput(arg2 != null && arg2);
					MainController.getInstance().setChanged();
				}
			});
			new CustomTooltip("Do you want to allow users to turn this filter on and off?").install(input);
			
			HBox buttons = new HBox();
			buttons.setAlignment(Pos.CENTER_LEFT);
			Button remove = new Button();
			remove.setGraphic(MainController.loadFixedSizeGraphic("icons/delete.png", 12));
			remove.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					crudFilters.remove(filter);
					// redraw this section
					redrawer.redraw();
					MainController.getInstance().setChanged();
				}
			});
			Button up = new Button();
			up.setGraphic(MainController.loadFixedSizeGraphic("move/up.png", 12));
			up.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					int indexOf = crudFilters.indexOf(filter);
					if (indexOf > 0) {
						crudFilters.remove(indexOf);
						crudFilters.add(indexOf - 1, filter);
					}
					// redraw this section
					redrawer.redraw();
					MainController.getInstance().setChanged();
				}
			});
			
			Button down = new Button();
			down.setGraphic(MainController.loadFixedSizeGraphic("move/down.png", 12));
			down.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					int indexOf = crudFilters.indexOf(filter);
					if (indexOf < crudFilters.size() - 1) {
						crudFilters.remove(indexOf);
						crudFilters.add(indexOf + 1, filter);
					}
					// redraw this section
					redrawer.redraw();
					MainController.getInstance().setChanged();
				}
			});
			
			buttons.getChildren().addAll(up, down, remove);
			HBox.setMargin(mainOperator, new Insets(10, 0, 10, 0));
			HBox.setMargin(alias, new Insets(10, 0, 10, 10));
			HBox.setMargin(field, new Insets(10, 0, 10, 10));
			HBox.setMargin(operator, new Insets(10, 0, 10, 10));
			HBox.setMargin(input, new Insets(10, 0, 10, 10));
			HBox.setMargin(vary, new Insets(10, 0, 10, 10));
			HBox.setMargin(buttons, new Insets(10, 0, 10, 10));
//			filterBox.getChildren().addAll(mainOperator, alias, field, operator, buttons, input, vary);
			filterBox.getChildren().addAll(mainOperator, field, operator, alias, buttons, input, vary);
			filters.getChildren().addAll(filterBox);
		}
		
		// have a button to add a filter
		Button add = new Button("Filter");
		add.setGraphic(MainController.loadFixedSizeGraphic("icons/add.png", 12));
		add.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				crudFilters.add(new CRUDFilter());
				// redraw this section
				redrawer.redraw();
				MainController.getInstance().setChanged();
			}
		});
		Button addAll = new Button("All Filters");
		addAll.setGraphic(MainController.loadFixedSizeGraphic("icons/add-list.png", 12));
		addAll.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				List<String> usedFields = new ArrayList<String>();
				for (CRUDFilter filter : crudFilters) {
					if (filter.getKey() != null) {
						usedFields.add(filter.getKey());
					}
				}
				for (String field : fields(foreignFields, coreType, true)) {
					if (!usedFields.contains(field)) {
						CRUDFilter filter = new CRUDFilter();
						filter.setKey(field);
						filter.setOperator("=");
						crudFilters.add(filter);
					}
				}
				// redraw this section
				redrawer.redraw();
				MainController.getInstance().setChanged();
			}
		});
		
		HBox buttons = new HBox();
		buttons.getStyleClass().add("buttons");
		buttons.getChildren().addAll(add);
		if (allowAddAll) {
			buttons.getChildren().addAll(addAll);	
		}
		filters.getChildren().add(buttons);
	}
	
	// currently we are limited to selecting from the table itself, not yet parent tables etc
	// this is possible but...complex
	private Map<String, List<String>> getForeignFields(CRUDArtifact instance) {
		Map<String, List<String>> fields = new HashMap<String, List<String>>();
		for (Element<?> element : TypeUtils.getAllChildren((ComplexType) instance.getConfig().getCoreType())) {
			Value<String> property = element.getProperty(ForeignKeyProperty.getInstance());
			if (property != null && property.getValue() != null) {
				String[] split = property.getValue().split(":");
				DefinedType referencedType = (DefinedType) instance.getRepository().resolve(split[0]);
				if (referencedType != null) {
					List<String> list = new ArrayList<String>();
					for (Element<?> child : JDBCUtils.getFieldsInTable((ComplexType) referencedType)) {
						list.add(child.getName());
					}
					fields.put(element.getName(), list);
				}
			}
		}
		return fields;
	}
	
	private static Map<String, String> getForeignKeys(List<ForeignNameField> foreignFields) {
		Map<String, String> keys = new HashMap<String, String>();
		// suppose your type extends another type (e.g. node) and you want to use a foreign key from that parent type, it "should" be possible. the expansion should get the correct binding
		for (ForeignNameField element : foreignFields) {
			if (element.getForeignKey() != null) {
				keys.put(element.getLocalName(), element.getForeignKey());
			}
		}
		return keys;
	}
	private static Map<String, String> getForeignKeys(ComplexType type) {
		Map<String, String> keys = new HashMap<String, String>();
		// suppose your type extends another type (e.g. node) and you want to use a foreign key from that parent type, it "should" be possible. the expansion should get the correct binding
		for (Element<?> element : TypeUtils.getAllChildren(type)) {
			Value<String> property = element.getProperty(ForeignKeyProperty.getInstance());
			if (property != null && property.getValue() != null) {
				keys.put(element.getName(), property.getValue());
			}
		}
		return keys;
	}
	private static List<String> getChildren(ComplexType type) {
		List<String> children = new ArrayList<String>();
		for (Element<?> element : TypeUtils.getAllChildren(type)) {
			children.add(element.getName());
		}
		return children;
	}
	
	public static void drawForeignNameFields(List<ForeignNameField> foreignFields, DefinedType coreType, Repository repository, VBox main) {
		Map<String, String> foreignKeys = getForeignKeys((ComplexType) coreType);
		// you need at least some foreign keys in your current table to do this
		//if (!foreignKeys.isEmpty()) {
			Label foreign = new Label("Import Fields");
			foreign.getStyleClass().add("h1");
			main.getChildren().add(foreign);
			// if we have any foreign keys, we can use that to add foreign fields
			Label label = new Label("Add fields from referenced tables:");
			label.getStyleClass().add("p");
			main.getChildren().add(label);
			
			VBox vbox = new VBox();
			main.getChildren().add(vbox);
			drawExistingFields(foreignFields, coreType, repository, vbox);
		//}
	}
	
	private static void drawExistingFields(List<ForeignNameField> foreignFields, DefinedType coreType, Repository repository, VBox main) {
		main.getChildren().clear();
		// we first render the already added keys
		for (ForeignNameField field : foreignFields) {
			HBox box = new HBox();
			box.setAlignment(Pos.CENTER_LEFT);
			Label localName = new Label(field.getLocalName());
			localName.setPadding(new Insets(5));
			localName.getStyleClass().add("crud-field-name");
			
			Label remoteName = new Label("(" + field.getForeignName() + ")");
			remoteName.setPadding(new Insets(5));
			remoteName.getStyleClass().add("crud-foreign-name");
			
			Button remove = new Button();
			remove.setGraphic(MainController.loadFixedSizeGraphic("icons/delete.png", 12));
			remove.getStyleClass().add("crud-remove");
			
			// remove a field
			remove.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent arg0) {
					foreignFields.remove(field);
					drawExistingFields(foreignFields, coreType, repository, main);
					MainController.getInstance().setChanged();
				}
			});
			new CustomTooltip("Remove this imported field").install(remove);
			
			// add a foreign key to a particular field so you can build on top of that
			Button reference = new Button();
			reference.setGraphic(MainController.loadFixedSizeGraphic("move/right.png", 12));
			reference.getStyleClass().add("crud-reference");
			reference.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent arg0) {
					SimpleProperty<String> simpleProperty = new SimpleProperty<String>("Foreign Key", String.class, false);
					EAIDeveloperUtils.buildPopup(MainController.getInstance(), "Set foreign key", Arrays.asList(simpleProperty), new PropertiesHandler() {
						@Override
						public void handle(SimplePropertyUpdater updater) {
							Object value = updater.getValue("Foreign Key");
							if (value == null && field.getForeignKey() != null) {
								MainController.getInstance().setChanged();
								field.setForeignKey(null);
							}
							else if (value != null && !value.equals(field.getForeignKey())) {
								MainController.getInstance().setChanged();
								field.setForeignKey(value.toString());
							}
						}
					}, false, MainController.getInstance().getActiveStage(), new ValueImpl<String>(simpleProperty, field.getForeignKey()));
				}
			});
			new CustomTooltip("Update the foreign key for this field").install(reference);
			
			box.getChildren().addAll(remove, reference, localName, remoteName);
			main.getChildren().add(box);
		}
		// allow adding of new field
		TextField name = new TextField();
		name.setPromptText("Field name");
		
		StringProperty foreignName = new SimpleStringProperty();
		StringProperty fieldName = new SimpleStringProperty();
		
		name.promptTextProperty().bind(fieldName);
		
		// @2021-06-02: you can insert custom foreign keys on fields that were originally not foreign-keyed
		// this means however, to be able to do that you must be able to select the keys first
		// so we allow all fields to be selected
		// note that if this seems too confusing, we can always add a checkbox to explicitly toggle this behavior
		CheckBox foreignFieldsOnly = new CheckBox();
		foreignFieldsOnly.setSelected(true);
		new CustomTooltip("If selected, only available foreign keys are shown, if you disable this you can access all the fields in the core type but not additionally imported fields").install(foreignFieldsOnly);
		
		HBox combo = new HBox();
		drawCombo(repository, foreignName, fieldName, (ComplexType) coreType, combo, foreignFieldsOnly.isSelected(), foreignFields);
//		drawCombo(repository, foreignName, fieldName, (ComplexType) coreType, combo, true, foreignFields);
		
		foreignFieldsOnly.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
				combo.getChildren().clear();
				drawCombo(repository, foreignName, fieldName, (ComplexType) coreType, combo, arg2, foreignFields);
			}
		});
		
		Button add = new Button();
		add.setGraphic(MainController.loadFixedSizeGraphic("icons/add.png", 12));
		HBox box = new HBox();
		box.setAlignment(Pos.CENTER_LEFT);
		box.getChildren().addAll(name, combo, foreignFieldsOnly, add);
		box.setPadding(new Insets(10, 0, 0, 0));
		main.getChildren().add(box);
		HBox.setMargin(combo, new Insets(0, 10, 0, 10));
		HBox.setMargin(add, new Insets(0, 10, 0, 10));
		HBox.setMargin(foreignFieldsOnly, new Insets(0, 10, 0, 10));
		
		add.disableProperty().bind(foreignName.isNull().or(foreignName.isEmpty()));
		add.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				ForeignNameField field = new ForeignNameField();
				if (name.getText().trim().isEmpty()) {
					field.setLocalName(fieldName.get());
				}
				else {
					field.setLocalName(NamingConvention.LOWER_CAMEL_CASE.apply(NamingConvention.UNDERSCORE.apply(name.getText())));
				}
				field.setForeignName(foreignName.get());
				foreignFields.add(field);
				drawExistingFields(foreignFields, coreType, repository, main);
				MainController.getInstance().setChanged();
			}
		});
	}
	
	// the first combo box is from the current type and you _must_ choose a foreign
	// every other combo box after that can contain any field
	private static void drawCombo(Repository repository, StringProperty field, StringProperty name, ComplexType type, HBox combos, boolean limitToForeign, List<ForeignNameField> foreignFields) {
		List<String> children = getChildren(type);
		Map<String, String> foreignKeys = getForeignKeys(type);
		// add self defined foreign keys in imported fields
		foreignKeys.putAll(getForeignKeys(foreignFields));
		
		if ((limitToForeign && !foreignKeys.isEmpty()) || (!limitToForeign && !children.isEmpty())) {
			// populate the combobox with foreign keys
			ComboBox<String> box = new ComboBox<String>();
			box.getItems().add(null);
			
			List<String> fields = new ArrayList<String>(limitToForeign ? foreignKeys.keySet() : children);
			Collections.sort(fields);
			box.getItems().addAll(fields);
			
			combos.getChildren().add(box);
			box.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					// reset
					field.set(null);
					name.set(null);
					// if we change the value of the combo box, clear anything we drew after this one
					int indexOf = combos.getChildren().indexOf(box);
					// don't remove this combo itself
					if (indexOf < combos.getChildren().size() - 1) {
						// some really odd behavior if you use the sublist to immediately removeAll, you get concurrent modification, we add it to a new list to circumvent this
						List<Node> subList = new ArrayList<Node>(combos.getChildren().subList(indexOf + 1, combos.getChildren().size()));
						combos.getChildren().removeAll(subList);
					}
					if (arg2 != null) {
						// recombine the fields
						StringBuilder foreignNameBuilder = new StringBuilder();
						StringBuilder fieldNameBuilder = new StringBuilder();
						for (Node child : combos.getChildren()) {
							if (child instanceof ComboBox) {
								if (!foreignNameBuilder.toString().isEmpty()) {
									foreignNameBuilder.append(":");
								}
								String singleName = ((ComboBox<?>) child).getSelectionModel().getSelectedItem().toString();
								// for the foreignName we need the full field name
								foreignNameBuilder.append(singleName);
								// for the suggested field name we do not!
								if (singleName.endsWith("Id")) {
									singleName = singleName.substring(0, singleName.length() - "Id".length());
								}
								if (!fieldNameBuilder.toString().isEmpty()) {
									singleName = singleName.substring(0, 1).toUpperCase() + singleName.substring(1);
								}
								fieldNameBuilder.append(singleName);
							}
						}
						field.set(foreignNameBuilder.toString());
						name.set(fieldNameBuilder.toString());
						// you selected another foreign key, optionally keep going (if you add at this point, you get the key field itself)
						if (foreignKeys.containsKey(arg2)) {
							String foreignKeyValue = foreignKeys.get(arg2);
							String[] split = foreignKeyValue.split(":");
							Artifact resolve = repository.resolve(split[0]);
							if (resolve instanceof ComplexType) {
								drawCombo(repository, field, name, (ComplexType) resolve, combos, false, foreignFields);
							}
						}
					}
				}
			});
		}
	}
	
	private String suggestFieldName(String key, String field) {
		if (key.endsWith("Id")) {
			key = key.substring(0, key.length() - 2);
		}
		if (field != null) {
			key += field.substring(0, 1).toUpperCase() + field.substring(1);
		}
		return key;
	}
	
	// not compatible with future vision of having as many views as you want!
	private Button buildCopyButton(String title, List<String> fromBlacklist, List<String> toBlacklist) {
		Button button = new Button(title);
		button.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				toBlacklist.clear();
				toBlacklist.addAll(fromBlacklist);
				MainController.getInstance().setChanged();
			}
		});
		return button;
	}
	
	private void populateChecklist(CRUDArtifact instance, Pane pane, List<String> list, List<String> toIgnore, boolean respectProviderBlacklist, boolean allowCopyPaste, Button...buttons) {
		TextField search = new TextField();
		search.setPromptText("Search fields");
		TilePane checkboxes = new TilePane();
		checkboxes.setOrientation(Orientation.VERTICAL);
		// the rows/columns
		checkboxes.setAlignment(Pos.TOP_LEFT);
		// the content within
		checkboxes.setTileAlignment(Pos.CENTER_LEFT);
		checkboxes.setPrefRows(5);
		for (String field : fields(instance.getConfig().getForeignFields(), instance.getConfig().getCoreType(), false)) {
			if (toIgnore.indexOf(field) >= 0) {
				continue;
			}
			if (respectProviderBlacklist && instance.getConfig().getProvider() != null && instance.getConfig().getProvider().getConfig().getBlacklistedFields() != null && instance.getConfig().getProvider().getConfig().getBlacklistedFields().contains(field)) {
				continue;
			}
			CheckBox box = new CheckBox(field);
			box.setId(field);
			box.setSelected(list.indexOf(field) >= 0);
			box.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
					if (arg2 != null && arg2) {
						if (list.indexOf(field) < 0) {
							list.add(field);
						}
					}
					else {
						list.remove(field);
					}
					MainController.getInstance().setChanged();
				}
			});
			TilePane.setMargin(box, new Insets(3, 20, 0, 0));
			checkboxes.getChildren().add(box);
		}
		search.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				for (Node node : checkboxes.getChildren()) {
					CheckBox box = (CheckBox) node;
					boolean visible = newValue == null || newValue.trim().isEmpty() || box.getId().toLowerCase().contains(newValue.toLowerCase());
					box.setManaged(visible);
					box.setVisible(visible);
				}
			}
		});
		// we want approximitely 4 columns (max) with at least 5 per column
		checkboxes.setPrefRows((int) Math.max(5, Math.ceil(checkboxes.getChildren().size() / 4)));
		
		VBox vbox = new VBox();
		vbox.setSpacing(10);
		HBox top = new HBox();
		top.setSpacing(10);
		Button selectAll = new Button("Select all");
		selectAll.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				for (Node node : checkboxes.getChildren()) {
					CheckBox box = (CheckBox) node;
					box.setSelected(true);
				}
			}
		});
		Button deselectAll = new Button("Deselect all");
		deselectAll.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				for (Node node : checkboxes.getChildren()) {
					CheckBox box = (CheckBox) node;
					box.setSelected(false);
				}
			}
		});
		Button paste = new Button("Paste selection");
		Button copy = new Button("Copy selection");
		copy.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				blacklistClipboard.clear();
				blacklistClipboard.addAll(list);
			}
		});
		paste.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				for (Node node : checkboxes.getChildren()) {
					CheckBox box = (CheckBox) node;
					box.setSelected(blacklistClipboard.indexOf(box.getId()) >= 0);
				}
			}
		});
		paste.disableProperty().bind(Bindings.size(blacklistClipboard).isEqualTo(0));
		top.getChildren().addAll(search, selectAll, deselectAll);
		if (allowCopyPaste) {
			top.getChildren().addAll(copy, paste);	
		}
		if (buttons != null && buttons.length > 0) {
			top.getChildren().addAll(buttons);
		}
		vbox.getChildren().addAll(top, checkboxes);
		pane.getChildren().add(vbox);
	}
	
	private static List<String> fields(List<ForeignNameField> foreignFields, DefinedType coreType, boolean includeForeignFields) {
		List<String> list = new ArrayList<String>();
		for (Element<?> child : TypeUtils.getAllChildren((ComplexType) coreType)) {
			if (child.getType() instanceof SimpleType) {
				list.add(child.getName());
			}
		}
		if (includeForeignFields && foreignFields != null) {
			for (ForeignNameField foreign : foreignFields) {
				if (foreign.getLocalName() != null) {
					list.add(foreign.getLocalName());
				}
			}
		}
		Collections.sort(list);
		return list;
	}
	
	private static ComboBox<String> newFieldCombo(List<ForeignNameField> foreignFields, DefinedType coreType, boolean includeForeignFields) {
		ComboBox<String> fields = new ComboBox<String>();
		fields.getItems().addAll(fields(foreignFields, coreType, includeForeignFields));
		fields.getItems().add(0, null);
		return fields;
	}
	
	@Override
	public String getCategory() {
		return "Services";
	}
}
