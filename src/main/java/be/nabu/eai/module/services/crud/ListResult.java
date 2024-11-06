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