package be.nabu.eai.module.services.crud.collection;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.developer.api.CollectionManager;
import be.nabu.eai.developer.collection.EAICollectionUtils;
import be.nabu.eai.repository.api.Entry;
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
		List<Entry> services = new ArrayList<Entry>();
		addIfExists(services, "list");
		addIfExists(services, "create");
		addIfExists(services, "delete");
		addIfExists(services, "update");
		addIfExists(services, "get");
		buttons.add(EAICollectionUtils.newDeleteButton(entry, null));
		return EAICollectionUtils.newSummaryTile(entry, "crud-big.png", services, buttons);
	}
	
}
