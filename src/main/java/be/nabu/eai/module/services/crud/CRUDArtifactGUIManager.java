package be.nabu.eai.module.services.crud;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.module.services.crud.provider.CRUDProviderArtifact;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.util.Callback;

// for list, we could allow configuring a custom jdbc service? if relations get too complex, it might be necessary?
public class CRUDArtifactGUIManager extends BaseJAXBGUIManager<CRUDConfiguration, CRUDArtifact> {

	public CRUDArtifactGUIManager() {
		super("CRUD Services", CRUDArtifact.class, new CRUDArtifactManager(), CRUDConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return Arrays.asList(new SimpleProperty<DefinedType>("Type", DefinedType.class, true));
	}

	@Override
	protected CRUDArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		CRUDArtifact artifact = new CRUDArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
		if (values != null && values.length > 0) {
			artifact.getConfig().setCoreType((DefinedType) values[0].getValue());
		}
		if (artifact.getConfig().getCoreType() == null) {
			throw new IllegalStateException("You need to define a type");
		}
		return artifact;
	}

	@Override
	protected void display(CRUDArtifact instance, Pane pane) {
		// for list, create & update, we want to select the relevant fields to expose to the end user
		// for list we want to additionally set filters (both exposed as input or hardcoded)
		
		// we want some generic settings like which field to use for contextual security
		// for create, we may want to indicate like a parent field? which is used for security and to link?
		// we basically tell the service which field it is, at which point it will be added to the path and automatically mapped?
	
		Accordion accordion = new Accordion();
		
		AnchorPane generalPane = new AnchorPane();
		populateGeneral(instance, generalPane);
		generalPane.getStyleClass().add("configuration-pane");
		generalPane.getStyleClass().add("configuration-pane-basic");
		TitledPane general = new TitledPane("General", generalPane);
		accordion.getPanes().add(general);

		accordion.setExpandedPane(general);
		
		AnchorPane listPane = new AnchorPane();
		populateList(instance, listPane);
		listPane.getStyleClass().add("configuration-pane");
		listPane.getStyleClass().add("configuration-pane-basic");
		TitledPane list = new TitledPane("General", listPane);
		accordion.getPanes().add(list);
		
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
		HBox main = new HBox();
		main.setPadding(new Insets(10));
		
		// add crud provider
		ComboBox<CRUDProviderArtifact> artifacts = new ComboBox<CRUDProviderArtifact>();
		artifacts.setCellFactory(new Callback<ListView<CRUDProviderArtifact>, ListCell<CRUDProviderArtifact>>() {
			@Override
			public ListCell<CRUDProviderArtifact> call(ListView<CRUDProviderArtifact> param) {
				return new ListCell<CRUDProviderArtifact>() {
					@Override
					protected void updateItem(CRUDProviderArtifact arg0, boolean arg1) {
						super.updateItem(arg0, arg1);
						setText(arg0 == null ? null : arg0.getId());
					}
				};
			}
		});
		artifacts.getSelectionModel().select(instance.getConfig().getProvider());
		artifacts.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<CRUDProviderArtifact>() {
			@Override
			public void changed(ObservableValue<? extends CRUDProviderArtifact> arg0, CRUDProviderArtifact arg1, CRUDProviderArtifact arg2) {
				instance.getConfig().setProvider(arg2);
				MainController.getInstance().setChanged();
			}
		});
		
		// add base path
		TextField basePath = new TextField();
		basePath.setText(instance.getConfig().getBasePath());
		basePath.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				instance.getConfig().setBasePath(arg2 == null || arg2.trim().isEmpty() ? null : arg2);
				MainController.getInstance().setChanged();
			}
		});
		main.getChildren().add(EAIDeveloperUtils.newHBox("Base Path", basePath));
		
		// select the parent field
		ComboBox<String> parentField = newFieldCombo(instance);
		parentField.getSelectionModel().select(instance.getConfig().getParentField());
		parentField.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				instance.getConfig().setParentField(arg2 == null || arg2.trim().isEmpty() ? null : arg2);
				MainController.getInstance().setChanged();
			}
		});
		HBox parentFieldBox = EAIDeveloperUtils.newHBox("Parent Field", parentField);
		MainController.getInstance().attachTooltip((Label) parentFieldBox.getChildren().get(0), "Configure the parent field for this type, this is relevant for listing and creating");
		main.getChildren().add(parentFieldBox);
		
		// list and create have security field of parent
		// for the list, it has to be one of the filters, for create there has to be a field to store it in
		// update & delete have security field of child (by default the primary key, but you can for instance also set it on the parent)
		
		// allow for a fixed order by or by input (can be combined?)
		
		general.getChildren().add(main);
		maximize(main);
	}
	
	private void populateList(CRUDArtifact instance, Pane list) {
		if (instance.getConfig().getFilters() == null) {
			instance.getConfig().setFilters(new ArrayList<CRUDFilter>());
		}
		Label label = new Label("List Filters");
		HBox.setMargin(label, new Insets(10, 0, 10, 10));
		list.getChildren().add(label);
		
		// if you want to hardcode values, add it to the operator (for now)
		// first we define the filters
		for (CRUDFilter filter : instance.getConfig().getFilters()) {
			HBox filterBox = new HBox();
			ComboBox<String> field = newFieldCombo(instance);
			field.getSelectionModel().select(filter.getKey());
			field.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					filter.setKey(arg2 == null || arg2.trim().isEmpty() ? null : arg2);
					MainController.getInstance().setChanged();
				}
			});
			ComboBox<String> operator = new ComboBox<String>();
			operator.setValue(filter.getOperator());
			operator.getItems().addAll("=", "<>", ">", "<", ">=", "<=", "is null", "is not null");
			operator.valueProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					filter.setOperator(arg2 == null || arg2.trim().isEmpty() ? null : arg2);
					MainController.getInstance().setChanged();
				}
			});
			CheckBox input = new CheckBox("Is this filter an input?");
			input.setSelected(filter.isInput());
			input.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
					filter.setInput(arg2 != null && arg2);
					MainController.getInstance().setChanged();
				}
			});
			Button remove = new Button("Remove Filter");
			remove.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					instance.getConfig().getFilters().remove(filter);
					// redraw this section
					list.getChildren().clear();
					populateList(instance, list);
					MainController.getInstance().setChanged();
				}
			});
			list.getChildren().addAll(filterBox, operator, input, remove);
		}
		
		// have a button to add a filter
		Button add = new Button("Add Filter");
		add.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				instance.getConfig().getFilters().add(new CRUDFilter());
				// redraw this section
				list.getChildren().clear();
				populateList(instance, list);
				MainController.getInstance().setChanged();
			}
		});
		list.getChildren().add(add);
		
		// then checkboxes to choose the fields you want to blacklist
		label = new Label("Choose the fields you want to blacklist from the resultset:");
		HBox.setMargin(label, new Insets(10, 0, 10, 0));
		list.getChildren().add(label);
		if (instance.getConfig().getListFields() == null) {
			instance.getConfig().setListFields(new ArrayList<String>());
		}
		populateChecklist(instance, list, instance.getConfig().getListFields());
	}
	
	private void populateChecklist(CRUDArtifact instance, Pane pane, List<String> list, String...ignore) {
		List<String> toIgnore = Arrays.asList(ignore);
		HBox checkboxes = new HBox();
		for (String field : fields(instance)) {
			if (toIgnore.indexOf(field) >= 0) {
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
			checkboxes.getChildren().add(box);
		}
		pane.getChildren().add(checkboxes);
	}
	
	private List<String> fields(CRUDArtifact instance) {
		List<String> list = new ArrayList<String>();
		for (Element<?> child : TypeUtils.getAllChildren((ComplexType) instance.getConfig().getCoreType())) {
			if (child.getType() instanceof SimpleType) {
				list.add(child.getName());
			}
		}
		Collections.sort(list);
		return list;
	}
	private ComboBox<String> newFieldCombo(CRUDArtifact instance) {
		ComboBox<String> fields = new ComboBox<String>();
		fields.getItems().addAll(fields(instance));
		return fields;
	}
}
