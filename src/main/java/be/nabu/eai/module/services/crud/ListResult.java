package be.nabu.eai.module.services.crud;

import java.util.List;

public class ListResult {
	private List<Object> results;
	private Long rowCount, totalRowCount;
	private Boolean hasNext;
	public ListResult() {
		// auto
	}
	public ListResult(List<Object> results, Long rowCount, Long totalRowCount, Boolean hasNext) {
		this.results = results;
		this.rowCount = rowCount;
		this.totalRowCount = totalRowCount;
		this.hasNext = hasNext;
	}
	public Long getTotalRowCount() {
		return totalRowCount;
	}
	public void setTotalRowCount(Long totalRowCount) {
		this.totalRowCount = totalRowCount;
	}
	public Long getRowCount() {
		return rowCount;
	}
	public void setRowCount(Long rowCount) {
		this.rowCount = rowCount;
	}
	public Boolean isHasNext() {
		return hasNext;
	}
	public void setHasNext(Boolean hasNext) {
		this.hasNext = hasNext;
	}
	public List<Object> getResults() {
		return results;
	}
	public void setResults(List<Object> results) {
		this.results = results;
	}
}