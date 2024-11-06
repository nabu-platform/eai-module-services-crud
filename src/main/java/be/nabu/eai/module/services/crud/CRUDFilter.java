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

import be.nabu.eai.repository.util.Filter;

public class CRUDFilter extends Filter {
	// whether or not we want to expose this as input
	private boolean input;
	// you can expose this as a differently named field to the outside world
	// mostly relevant if you want multiple filters on the same field
	private String alias;
	
	// the naming is based on the vary header in http
	// the usecase is this: you want to perform a REST call with a number of filters
	// then you want to open a stream to listen to more data entries that match
	// whenever you update a data entry in a field that is not in the query, it doesn't matter
	// but if you update a field that is in the query, due to the update, it might not pass by the actual filter anymore
	// for a concrete example, let's say you want to get all the workflows in status ERROR
	// the resulting filter we could use for broadcasting would say status == "ERROR"
	// however, if at that point, someone updated it to FAILED, it wouldn't pass the filter
	// this means, the party looking at the stream does not see this potentially crucial update
	// to that end, filters can be different (from a streaming perspective) for CREATE and UPDATE actions
	// in general, we assume the UPDATE actions are less strict than the CREATE, only the create should end in a new entry being shown, the update should only ever update an in-place entry.
	// the vary simply marks the filters that are relevant for create and not for update so we can build our queries
	private boolean vary;
	
	public boolean isInput() {
		return input;
	}
	public void setInput(boolean input) {
		this.input = input;
	}

	public String getAlias() {
		return alias;
	}
	public void setAlias(String alias) {
		this.alias = alias;
	}
	public boolean isVary() {
		return vary;
	}
	public void setVary(boolean vary) {
		this.vary = vary;
	}
}
