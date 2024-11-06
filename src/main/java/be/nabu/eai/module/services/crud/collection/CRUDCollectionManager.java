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
