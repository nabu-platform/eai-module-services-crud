package be.nabu.eai.module.services.crud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.api.NodeContainer;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.module.services.crud.CRUDService.CRUDType;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.libs.property.api.Property;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

public class CRUDContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (entry.isNode() && CRUDArtifact.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
			Menu menu = new Menu("Interaction");
			MenuItem createListItem = new MenuItem("Add List View");
			createListItem.setGraphic(MainController.loadGraphic("add.png"));
			menu.getItems().add(createListItem);
			
			createListItem.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event) {
					try {
						List<Property<?>> supported = Arrays.asList(new SimpleProperty<String>("Name", String.class, true));
						SimplePropertyUpdater updater = new SimplePropertyUpdater(true, new LinkedHashSet(supported));
						EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Add List View", new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent arg0) {
								try {
									String name = updater.getValue("Name");
									if (name != null && !name.trim().isEmpty()) {
										CRUDArtifact artifact = (CRUDArtifact) entry.getNode().getArtifact();
										if (artifact.getConfig().getViews() == null) {
											artifact.getConfig().setViews(new ArrayList<CRUDView>());
										}
										CRUDView view = new CRUDView();
										view.setType(CRUDType.LIST);
										view.setName(name);
										artifact.getConfig().getViews().add(view);
										NodeContainer<?> container = MainController.getInstance().getContainer(artifact.getId());
										if (container != null) {
											container.setChanged(true);
											container.activate();
										}
										else {
											// save it
											new CRUDArtifactManager().save((ResourceEntry) artifact.getRepository().getEntry(artifact.getId()), artifact);
										}
									}
								}
								catch (Exception e) {
									MainController.getInstance().notify(e);
								}
							}
						});
					}
					catch (Exception e) {
						MainController.getInstance().notify(e);
					}
				}
			});
			return menu;
		}
		return null;
	}
}
