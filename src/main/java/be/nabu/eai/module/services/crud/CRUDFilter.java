package be.nabu.eai.module.services.crud;

import be.nabu.eai.repository.util.Filter;

public class CRUDFilter extends Filter {
	// whether or not we want to expose this as input
	private boolean input;
	
	public boolean isInput() {
		return input;
	}

	public void setInput(boolean input) {
		this.input = input;
	}

}
