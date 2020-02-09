package be.nabu.eai.module.services.crud;

import be.nabu.eai.repository.util.Filter;

public class CRUDFilter extends Filter {
	// whether or not we want to expose this as input
	private boolean input;
	// you can expose this as a differently named field to the outside world
	// mostly relevant if you want multiple filters on the same field
	private String alias;
	
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

}
