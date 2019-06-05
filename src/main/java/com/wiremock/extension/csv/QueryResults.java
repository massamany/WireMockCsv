/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Représente un ensemble de résultats (noms de colonnes + lignes)
 * avec des informations supplémentaires éventuelles permettant de moduler le JSon produit.
 */
public class QueryResults {

	private final String[] columns;

	private Set<String> maskedColumns;

	private Set<String> consideredAsJsonColumns;

	private final List<QueryResult> lines;

	private String resultType;

	/**
	 * Constructeur sans resultset.
	 * @param columns Result columns names
	 * @param lines Result lines
	 */
	public QueryResults(final String[] columns, final List<QueryResult> lines) {
		this.columns = columns;
		this.lines = lines;
	}

	/**
	 * Constructeur parsant un resultset SQL.
	 * @param rs Query results
	 * @param aliases Column names aliases
	 * @throws SQLException When an exception occurs in request execution
	 */
	public QueryResults(final ResultSet rs, final Map<String, Map<String, Object>> aliases) throws SQLException {
		final Map<String, Map<String, Object>> aliasesUp = new HashMap<>();
		if (aliases != null) {
			aliases.entrySet().forEach(e -> aliasesUp.put(e.getKey().toUpperCase(), e.getValue()));
		}

		final ResultSetMetaData data = rs.getMetaData();
		this.columns = new String[data.getColumnCount()];
		for (int i = 0 ; i < this.columns.length ; i++) {
			String columnLabel = data.getColumnLabel(i + 1);
			final Map<String, Object> tableAliases = aliasesUp.get(data.getTableName(i + 1));
			if (tableAliases != null) {
				if (tableAliases.containsKey("columns")) {
					@SuppressWarnings("unchecked")
					final Map<String, String> columnsAliases = (Map<String, String>) tableAliases.get("columns");
					if (columnsAliases.containsKey(columnLabel)) {
						columnLabel = columnsAliases.get(columnLabel);
					}
				}
				if (tableAliases.containsKey("prefix")) {
					columnLabel = tableAliases.get("prefix") + "__" + columnLabel;
				}
			}
			this.columns[i] = columnLabel;
		}

		this.lines = new ArrayList<>();
		while (rs.next()) {
			final Object[] line = new Object[this.columns.length];
			for (int i = 0 ; i < this.columns.length ; i++) {
				line[i] = rs.getObject(i + 1);
			}
			this.lines.add(new QueryResult(line));
		}
	}

	public String[] getColumns() {
		return this.columns;
	}

	public List<QueryResult> getLines() {
		return this.lines;
	}

	public Set<String> getMaskedColumns() {
		return this.maskedColumns;
	}

	public void setMaskedColumns(final Set<String> maskedColumns) {
		this.maskedColumns = maskedColumns;
	}

	public boolean isMasked(final String columnName) {
		return this.maskedColumns != null && this.maskedColumns.contains(columnName);
	}

	public Set<String> getConsideredAsJsonColumns() {
		return consideredAsJsonColumns;
	}

	public void setConsideredAsJsonColumns(Set<String> considerAsJsonColumns) {
		this.consideredAsJsonColumns = considerAsJsonColumns;
	}

	public boolean isConsideredAsJson(final String columnName) {
		return this.consideredAsJsonColumns != null && this.consideredAsJsonColumns.contains(columnName);
	}

	public String getResultType() {
		return this.resultType;
	}

	public void setResultType(final String resultType) {
		this.resultType = resultType;
	}

	/**
	 * Représente une ligne de résultat.
	 */
	public class QueryResult {
		private final Object[] result;
		private Map<String, QueryResults> subResults;
		private Map<String, List<QueryResults>> subResultsLists;

		public QueryResult(final Object[] line) {
			this.result = line;
		}

		public Object[] getResult() {
			return this.result;
		}
		public Map<String, QueryResults> getSubResults() {
			return this.subResults;
		}
		public void setSubResults(final Map<String, QueryResults> subResults) {
			this.subResults = subResults;
		}

		public Map<String, List<QueryResults>> getSubResultsLists() {
			return this.subResultsLists;
		}

		public void setSubResultsLists(final Map<String, List<QueryResults>> subResultsLists) {
			this.subResultsLists = subResultsLists;
		}

		public String[] getColumns() {
			return QueryResults.this.columns;
		}

		public boolean isMasked(final String columnName) {
			return QueryResults.this.isMasked(columnName);
		}

		public boolean isConsideredAsJson(final String columnName) {
			return QueryResults.this.isConsideredAsJson(columnName);
		}
	}
}
