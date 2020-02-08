package be.nabu.eai.module.services.crud;

import java.util.List;

public class CRUDFilter {
	private String key, operator;
	private List<String> values;
	// whether or not we want to expose this as input
	private boolean input;
	
	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public List<String> getValues() {
		return values;
	}

	public void setValues(List<String> values) {
		this.values = values;
	}

	public String getOperator() {
		return operator;
	}

	public void setOperator(String operator) {
		this.operator = operator;
	}

	public boolean isInput() {
		return input;
	}

	public void setInput(boolean input) {
		this.input = input;
	}
	
}
