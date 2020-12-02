package be.nabu.eai.module.services.crud.collection;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.CollectionManager;
import be.nabu.eai.developer.collection.EAICollectionUtils;
import be.nabu.eai.developer.impl.CustomTooltip;
import be.nabu.eai.module.services.crud.CRUDArtifact;
import be.nabu.eai.module.services.crud.CRUDArtifactManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.libs.types.api.ComplexType;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;

public class CRUDCollectionManager implements CollectionManager {

	private Entry entry;

	public CRUDCollectionManager(Entry entry) {
		this.entry = entry;
	}

	@Override
	public Entry getEntry() {
		return entry;
	}

	@Override
	public boolean hasSummaryView() {
		return true;
	}

	private void addIfExists(List<Entry> services, String name) {
		Entry child = entry.getRepository().getEntry(entry.getId() + ".services." + name);
		if (child != null) {
			services.add(child);
		}
	}
	
	@Override
	public Node getSummaryView() {
		List<Button> buttons = new ArrayList<Button>();
		buttons.add(EAICollectionUtils.newViewButton(entry));
		String id = (String) buttons.get(0).getUserData();
		CRUDArtifact artifact = (CRUDArtifact) MainController.getInstance().getRepository().resolve(id);
		List<Entry> services = new ArrayList<Entry>();
		addIfExists(services, "list");
		addIfExists(services, "create");
		addIfExists(services, "delete");
		addIfExists(services, "update");
		addIfExists(services, "get");
		
		if (artifact.getConfig().getCoreType() != null) {
			List<String> primary = CRUDArtifactManager.getPrimary((ComplexType) artifact.getConfig().getCoreType());
			// for each action (list, create, update, delete) we add a button! + and - for add and delete
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getListService() != null) {
				
				Button list = new Button();
				new CustomTooltip("List all the available records").install(list);
				list.setGraphic(MainController.loadGraphic("icons/list.png"));
				list.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						MainController.getInstance().open(id + ".services.list");
					}
				});
				buttons.add(list);
				EAICollectionUtils.makeDraggable(list, id + ".services.list");
			}
			if (artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getCreateService() != null) {
				Button create = new Button();
				new CustomTooltip("Create a new record").install(create);
				create.setGraphic(MainController.loadFixedSizeGraphic("icons/add.png", 12));
				create.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						MainController.getInstance().open(id + ".services.create");
					}
				});
				buttons.add(create);
				EAICollectionUtils.makeDraggable(create, id + ".services.create");
			}
			if (!primary.isEmpty() && artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getDeleteService() != null) {
				Button delete = new Button();
				new CustomTooltip("Delete an existing record").install(delete);
				delete.setGraphic(MainController.loadFixedSizeGraphic("icons/minus.png", 12));
				delete.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						MainController.getInstance().open(id + ".services.delete");
					}
				});
				buttons.add(delete);
				EAICollectionUtils.makeDraggable(delete, id + ".services.delete");
			}
			if (!primary.isEmpty() && artifact.getConfig().getProvider() != null && artifact.getConfig().getProvider().getConfig().getUpdateService() != null) {
				Button update = new Button();
				new CustomTooltip("Update an existing record").install(update);
				update.setGraphic(MainController.loadFixedSizeGraphic("icons/edit.png", 12));
				update.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						MainController.getInstance().open(id + ".services.update");
					}
				});
				buttons.add(update);
				EAICollectionUtils.makeDraggable(update, id + ".services.update");
			}
		}
		buttons.add(EAICollectionUtils.newDeleteButton(entry, null));
		return EAICollectionUtils.newSummaryTile(entry, "crud-big.png", services, buttons);
	}
	
}
