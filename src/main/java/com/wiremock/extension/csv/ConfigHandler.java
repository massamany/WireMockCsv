/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.common.ListOrSingle;
import com.github.tomakehurst.wiremock.extension.responsetemplating.RequestTemplateModel;
import com.github.tomakehurst.wiremock.http.Request;
import com.wiremock.extension.csv.QueryResults.QueryResult;

/**
 * Permet de gérer la configuration et les paramètres à l'exécution d'une requête.<br>
 * Cette classe permet d'empiler la config globale, les paramètres HTTP, les paramètres custom et un ou plusieurs résultats de requête.
 */
public class ConfigHandler {
	private final Map<String, Object> globalConfig;
	private final DbManager manager;

	public ConfigHandler(final DbManager manager, final JsonConverter jsonConverter) throws WireMockCsvException {
		final File configFile = new File(WireMockCsvUtils.getFilesRoot() + File.separatorChar + "csv" + File.separatorChar + "WireMockCsv.json.conf");
		if (configFile.exists()) {
			this.globalConfig = Collections.unmodifiableMap(jsonConverter.readJsonToMap(configFile));
		} else {
			this.globalConfig = Collections.emptyMap();
		}
		this.manager = manager;
	}

	public Map<String, Object> getGlobalConfig() {
		return this.globalConfig;
	}

	public RequestConfigHandler getRequestConfigHandler(final Request request, final Parameters parameters) throws WireMockCsvException {
		return new RootConfigHandler(request, parameters);
	}

	/**
	 * Interface représentant la configuration d'exécution d'un niveau de requête.
	 */
	public interface RequestConfigHandler {
		Request getRequest();
		Parameters getTransformerParameters();
		RequestConfigHandler addQueryResult(QueryResult qr, Map<String, Map<String, Object>> customParametersConfig)
				throws WireMockCsvException;
		QueryResult getNearestQueryResult();
		Object getParamValue(String paramName);
		List<?> getParamValues(String paramName);
	}

	private abstract class AbstractConfigHandler implements RequestConfigHandler {
		private Map<String, List<?>> customParameters;


		protected void initCustomParameters(final Map<String, Map<String, Object>> customParametersConfig) throws WireMockCsvException {
			if (customParametersConfig != null) {
				this.customParameters = new HashMap<>();
				for (final Map.Entry<String, Map<String, Object>> e: customParametersConfig.entrySet()) {
					this.initCustomParameter(e);
				}
			}
		}

		private void initCustomParameter(final Map.Entry<String, Map<String, Object>> e) throws WireMockCsvException {
			final String action = (String) e.getValue().get("action");
			if ("split".equals(action)) {
				final Object src = e.getValue().get("sourceParam");
				final Object regexp = e.getValue().get("regexp");
				final Object srcValue = this.getParamValue(src.toString());
				if (srcValue != null) {
					this.customParameters.put(e.getKey(), Arrays.asList(Pattern.compile(regexp.toString()).split(srcValue.toString())));
				} else {
					this.customParameters.remove(e.getKey());
				}
			} else if ("replace".equals(action)) {
				final Object src = e.getValue().get("sourceParam");
				final Object regexp = e.getValue().get("regexp");
				final Object replacement = e.getValue().getOrDefault("replacement", "");
				final Object srcValue = this.getParamValue(src.toString());
				if (srcValue != null) {
					this.customParameters.put(e.getKey(),
							Arrays.asList(Pattern.compile(regexp.toString()).matcher(srcValue.toString()).replaceAll(replacement.toString())));
				} else {
					this.customParameters.remove(e.getKey());
				}
			} else if ("concatenate".equals(action)) {
				final Object src = e.getValue().get("sourceParam");
				final Object prefix = e.getValue().getOrDefault("prefix", "");
				final Object suffix = e.getValue().getOrDefault("suffix", "");
				final Object separator = e.getValue().getOrDefault("separator", "");
				final List<?> srcValues = this.getParamValues(src.toString());
				final StringBuilder sb = new StringBuilder();
				sb.append(prefix);
				if (srcValues != null && !srcValues.isEmpty()) {
					sb.append(srcValues.get(0));
					for (int i = 1 ; i < srcValues.size() ; ++i) {
						sb.append(separator).append(srcValues.get(i));
					}
				}
				sb.append(suffix);
				this.customParameters.put(e.getKey(), Arrays.asList(sb.toString()));
			} else if ("fromQuery".equals(action)) {
				Object query = e.getValue().get("query");
				query = WireMockCsvUtils.replaceQueryVariables(query.toString(), this);
				final QueryResults result = ConfigHandler.this.manager.select(query.toString(), null);
				if (result.getColumns().length > 1 && result.getLines().size() > 1) {
					throw new WireMockCsvException(
							"Can't handle queries with multiple columns and lines in fromQuery custom parameter");
				}
				if (result.getLines().size() > 1) {
					// Liste de toutes les lignes, première colonne uniquement.
					this.customParameters.put(e.getKey(),
							result.getLines().stream().map(qr -> qr.getResult()[0]).collect(Collectors.toList()));
				} else {
					if (result.getLines().isEmpty()) {
						// Pas de résultat
						this.customParameters.remove(e.getKey());
					} else {
						// Liste de toutes les colonnes
						this.customParameters.put(e.getKey(), Arrays.asList(result.getLines().get(0).getResult()));
					}
				}
			} else if ("escapeSql".equals(action)) {
				final Object src = e.getValue().get("sourceParam");
				final List<?> srcValue = this.getParamValues(src.toString());
				if (srcValue != null) {
					this.customParameters.put(e.getKey(),
							srcValue.stream().map(o -> o == null ? null : o.toString().replaceAll("'", "''")).collect(Collectors.toList()));
				} else {
					this.customParameters.remove(e.getKey());
				}
			} else {
				throw new WireMockCsvException("Unknown action: " + action);
			}
		}

		protected Map<String, List<?>> getCustomParameters() {
			return this.customParameters;
		}
	}

	/**
	 * Classe permettant de stocker le premier niveau de configuration.
	 */
	private class RootConfigHandler extends AbstractConfigHandler {
		private final Request request;
		private final Map<String, ListOrSingle<String>> requestParams;
		private final Parameters transformerParameters;

		public RootConfigHandler(final Request request, final Parameters transformerParameters) throws WireMockCsvException {
			this.request = request;
			this.requestParams = RequestTemplateModel.from(request).getQuery();
			this.transformerParameters = transformerParameters;
			@SuppressWarnings("unchecked")
			final Map<String, Map<String, Object>> customParametersConfig =
			(Map<String, Map<String, Object>>) this.transformerParameters.get("customParameters");
			this.initCustomParameters(customParametersConfig);
		}

		@Override
		public Request getRequest() {
			return this.request;
		}

		@Override
		public Parameters getTransformerParameters() {
			return this.transformerParameters;
		}

		@Override
		public RequestConfigHandler addQueryResult(final QueryResult qr, final Map<String, Map<String, Object>> customParametersConfig)
				throws WireMockCsvException {
			return new QueryResultConfigHandler(this, qr, customParametersConfig);
		}

		@Override
		public QueryResult getNearestQueryResult() {
			return null;
		}

		@Override
		public Object getParamValue(final String paramName) {
			List<?> value;
			if (this.getCustomParameters() != null && this.getCustomParameters().containsKey(paramName)) {
				value = this.getCustomParameters().get(paramName);
			} else {
				value = this.requestParams.get(paramName);
			}
			return value == null || value.isEmpty() ? null : value.get(0);
		}

		@Override
		public List<?> getParamValues(final String paramName) {
			List<?> value;
			if (this.getCustomParameters() != null && this.getCustomParameters().containsKey(paramName)) {
				value = this.getCustomParameters().get(paramName);
			} else {
				value = this.requestParams.get(paramName);
			}
			return value;
		}
	}

	/**
	 * Classe permettant de stocker les niveaux suivant de configuration.
	 */
	private class QueryResultConfigHandler extends AbstractConfigHandler {
		private final RequestConfigHandler parent;
		private final QueryResult queryResult;

		public QueryResultConfigHandler(final RequestConfigHandler parent, final QueryResult queryResult,
				final Map<String, Map<String, Object>> customParametersConfig) throws WireMockCsvException {
			super();
			this.parent = parent;
			this.queryResult = queryResult;
			this.initCustomParameters(customParametersConfig);
		}

		@Override
		public Request getRequest() {
			return this.parent.getRequest();
		}

		@Override
		public Parameters getTransformerParameters() {
			return this.parent.getTransformerParameters();
		}

		@Override
		public RequestConfigHandler addQueryResult(final QueryResult qr, final Map<String, Map<String, Object>> customParametersConfig)
				throws WireMockCsvException {
			return new QueryResultConfigHandler(this, qr, customParametersConfig);
		}

		@Override
		public QueryResult getNearestQueryResult() {
			return this.queryResult == null ? this.parent.getNearestQueryResult() : this.queryResult;
		}

		@Override
		public Object getParamValue(final String paramName) {
			if (this.getCustomParameters() != null && this.getCustomParameters().containsKey(paramName)) {
				List<?> paramValues = this.getCustomParameters().get(paramName);
				return paramValues.isEmpty() ? null : paramValues.get(0);
			} else if (this.queryResult != null) {
				for (int idx = 0 ; idx < this.queryResult.getColumns().length ; ++idx) {
					if (this.queryResult.getColumns()[idx].equals(paramName)) {
						return this.queryResult.getResult()[idx];
					}
				}
			}
			return this.parent.getParamValue(paramName);
		}

		@Override
		public List<?> getParamValues(final String paramName) {
			if (this.getCustomParameters() != null && this.getCustomParameters().containsKey(paramName)) {
				return this.getCustomParameters().get(paramName);
			} else if (this.queryResult != null) {
				for (int idx = 0 ; idx < this.queryResult.getColumns().length ; ++idx) {
					if (this.queryResult.getColumns()[idx].equals(paramName)) {
						final List<Object> res = new ArrayList<>(1);
						res.add(this.queryResult.getResult()[idx]);
						return res;
					}
				}
			}
			return this.parent.getParamValues(paramName);
		}
	}
}
