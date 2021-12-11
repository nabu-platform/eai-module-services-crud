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
