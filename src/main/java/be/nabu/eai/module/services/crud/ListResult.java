package be.nabu.eai.module.services.crud;

import java.util.List;

import be.nabu.libs.services.jdbc.api.Statistic;

public class ListResult {
	private List<Object> results;
	private List<Statistic> statistics;
	private Long rowCount, totalRowCount;
	public ListResult() {
		// auto
	}
	public ListResult(List<Object> results, Long rowCount, Long totalRowCount) {
		this.results = results;
		this.rowCount = rowCount;
		this.totalRowCount = totalRowCount;
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
	public List<Object> getResults() {
		return results;
	}
	public void setResults(List<Object> results) {
		this.results = results;
	}
	public List<Statistic> getStatistics() {
		return statistics;
	}
	public void setStatistics(List<Statistic> statistics) {
		this.statistics = statistics;
	}
}