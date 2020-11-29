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

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.impl.CustomTooltip;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;
import be.nabu.eai.module.services.crud.provider.CRUDProviderArtifact;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.jdbc.JDBCUtils;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.properties.ForeignKeyProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
		if (artifact.getConfig().getCoreType() == null) {
			throw new IllegalStateException("You need to define a type");
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
				"securityContextField", "parentField", "filters", "foreignFields", "coreType");
	}

	@Override
	protected void display(CRUDArtifact instance, Pane pane) {
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
		populateList(instance, listPane);
		listPane.getStyleClass().add("configuration-pane");
		listPane.getStyleClass().add("configuration-pane-basic");
		TitledPane list = new TitledPane("List", listPane);
		accordion.getPanes().add(list);
		
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
		
		accordion.getPanes().add(general);
		
		accordion.setExpandedPane(list);
		
		pane.getChildren().add(accordion);
		maximize(accordion);
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
		ComboBox<String> securityContextField = newFieldCombo(instance.getConfig().getForeignFields(), instance.getConfig().getCoreType(), false);
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
		populateChecklist(instance, main, instance.getConfig().getCreateBlacklistFields(), list, true);
		
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
		populateChecklist(instance, main, instance.getConfig().getUpdateBlacklistFields(), list, true);
		
		boolean foundAny = false;
		label = new Label("Choose the fields you want to regenerate:");
		VBox.setMargin(label, new Insets(10, 0, 10, 0));
		List<String> ignore = new ArrayList<String>();
		for (Element<?> element : TypeUtils.getAllChildren((ComplexType) instance.getConfig().getCoreType())) {
			if (!Date.class.isAssignableFrom(((SimpleType<?>) element.getType()).getInstanceClass())) {
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
			populateChecklist(instance, main, instance.getConfig().getUpdateRegenerateFields(), ignore, true);
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
	
	private void populateList(CRUDArtifact instance, Pane list) {
		VBox main = new VBox();
		main.setPadding(new Insets(10));
		list.getChildren().add(main);
		
		if (instance.getConfig().getFilters() == null) {
			instance.getConfig().setFilters(new ArrayList<CRUDFilter>());
		}
		
		Label label;
		drawFilters(instance.getConfig().getForeignFields(), instance.getConfig().getCoreType(), instance.getConfig().getFilters(), main, new Redrawer() {
			@Override
			public void redraw() {
				list.getChildren().clear();
				populateList(instance, list);				
			}
		}, true);

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
		if (instance.getConfig().getListBlacklistFields() == null) {
			instance.getConfig().setListBlacklistFields(new ArrayList<String>());
		}
		populateChecklist(instance, fields, instance.getConfig().getListBlacklistFields(), new ArrayList<String>(), false);
		
		VBox foreign = new VBox();
		foreign.getStyleClass().add("section");
		drawForeignNameFields(instance.getConfig().getForeignFields(), instance.getConfig().getCoreType(), instance.getRepository(), foreign);
		if (!foreign.getChildren().isEmpty()) {
			main.getChildren().add(foreign);
		}
	}

	public static interface Redrawer {
		public void redraw();
	}
	
	public static void drawFilters(List<ForeignNameField> foreignFields, DefinedType coreType, List<CRUDFilter> crudFilters, VBox main, Redrawer redrawer, boolean allowAddAll) {
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
			field.getSelectionModel().select(filter.getKey());
			field.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					filter.setKey(arg2 == null || arg2.trim().isEmpty() ? null : arg2);
					MainController.getInstance().setChanged();
				}
			});
			CheckBox input = new CheckBox();
			if (operatorIsInput(filter.getOperator())) {
				input.setDisable(true);
				filter.setInput(true);
				input.setVisible(false);
				input.setManaged(false);
			}
			
			ComboBox<String> operator = new ComboBox<String>();
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
			HBox.setMargin(mainOperator, new Insets(10, 10, 10, 0));
			HBox.setMargin(alias, new Insets(10));
			HBox.setMargin(field, new Insets(10));
			HBox.setMargin(operator, new Insets(10));
			HBox.setMargin(input, new Insets(10));
			HBox.setMargin(buttons, new Insets(10));
			filterBox.getChildren().addAll(mainOperator, alias, field, operator, buttons, input);
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
		if (!foreignKeys.isEmpty()) {
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
		}
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
			box.getChildren().addAll(remove, localName, remoteName);
			main.getChildren().add(box);
		}
		// allow adding of new field
		TextField name = new TextField();
		name.setPromptText("Field name");
		
		StringProperty foreignName = new SimpleStringProperty();
		StringProperty fieldName = new SimpleStringProperty();
		
		name.promptTextProperty().bind(fieldName);
		
		HBox combo = new HBox();
		drawCombo(repository, foreignName, fieldName, (ComplexType) coreType, combo, true);
		
		Button add = new Button();
		add.setGraphic(MainController.loadFixedSizeGraphic("icons/add.png", 12));
		HBox box = new HBox();
		box.setAlignment(Pos.CENTER_LEFT);
		box.getChildren().addAll(name, combo, add);
		box.setPadding(new Insets(10, 0, 0, 0));
		main.getChildren().add(box);
		HBox.setMargin(combo, new Insets(0, 10, 0, 10));
		HBox.setMargin(add, new Insets(0, 10, 0, 10));
		
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
	private static void drawCombo(Repository repository, StringProperty field, StringProperty name, ComplexType type, HBox combos, boolean limitToForeign) {
		List<String> children = getChildren(type);
		Map<String, String> foreignKeys = getForeignKeys(type);
		if ((limitToForeign && !foreignKeys.isEmpty()) || (!limitToForeign && !children.isEmpty())) {
			// populate the combobox with foreign keys
			ComboBox<String> box = new ComboBox<String>();
			
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
								drawCombo(repository, field, name, (ComplexType) resolve, combos, false);
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
	
	private void populateChecklist(CRUDArtifact instance, Pane pane, List<String> list, List<String> toIgnore, boolean respectProviderBlacklist) {
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
		// we want approximitely 4 columns (max) with at least 5 per column
		checkboxes.setPrefRows((int) Math.max(5, Math.ceil(checkboxes.getChildren().size() / 4)));
		pane.getChildren().add(checkboxes);
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
