/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ListOrSingle;
import com.github.tomakehurst.wiremock.extension.responsetemplating.RequestTemplateModel;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.http.Response.Builder;
import com.wiremock.extension.csv.QueryResults.QueryResult;

/**
 *
 * Classe d'extention WireMock.
 *
 */
public class WireMockCsv extends ResponseTransformer {

	private final DbManager manager;
	private final ConvJson convJson;

	public WireMockCsv() throws WireMockCsvException {
		try {
			this.manager = new DbManager(WireMockCsvServerRunner.filesRoot() + File.separatorChar + "csv" + File.separatorChar + "Database");
			this.manager.dbConnect();

			this.convJson = new ConvJson();
		} catch (final WireMockCsvException e) {
			WireMockConfiguration.wireMockConfig().notifier().error(e.getMessage(), e);
			throw new WireMockCsvException("Erreur lors de l'initialisation de l'extension CSV.");
		}
	}

	@Override
	public String getName() {
		return "wiremock-csv";
	}

	@Override
	public boolean applyGlobally() {
		return false;
	}

	@Override
	public Response transform(final Request request, final Response response, final FileSource files,
			final Parameters parameters) {
		try {
			final Map<String, Object> queries = new HashMap<>();
			queries.put("query", parameters.get("query"));
			queries.put("subqueries", parameters.get("subqueries"));
			queries.put("mask", parameters.get("mask"));
			queries.put("aliases", parameters.get("aliases"));
			queries.put("resultType", parameters.get("resultType"));
			
			Object structure = parameters.get("structure");
			String jsonStructure;
			if (structure == null) {
				jsonStructure = "{\"data\": \"${WireMockCsv}\" }";
			} else {
				jsonStructure = this.convJson.convertObjectToJson(structure);
			}

			final QueryResults qr = this.executeQueries(request, null, queries);
			Builder builder = Response.Builder.like(response).but().body(
					jsonStructure
					.replace("\"${WireMockCsv}\"", "${WireMockCsv}")
					.replace("${WireMockCsv}", this.convJson.convertToJson(qr))
			);
			if (qr.getLines().isEmpty() && parameters.containsKey("no-lines")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> noLines = (Map<String, Object>) parameters.get("no-lines");
				if (noLines.containsKey("status")) {
					builder.status((Integer) noLines.get("status"));
				}
				if (noLines.containsKey("statusMessage")) {
					builder.statusMessage((String) noLines.get("statusMessage"));
				}
			}
			return builder.build();
		} catch (final WireMockCsvException e) {
			WireMockConfiguration.wireMockConfig().notifier().error(e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}

	/**
	 *
	 * @param parametersQr Paramètres de la query principal
	 * @param queries Map contenant la query principale et ses sous queries
	 * @return Un nouveau QueryResults contenant les résultats et ses sous résultats
	 * @throws WireMockCsvException
	 */
	private QueryResults executeQueries(final Request request, final QueryResult parametersQr, final Map<String, Object> queries)
			throws WireMockCsvException {
		final QueryResults result;
		if (queries.get("query") != null) {
			@SuppressWarnings("unchecked")
			final Set<String> mask = new HashSet<>(queries.get("mask") == null ? Collections.emptyList() : (List<String>) queries.get("mask"));
			String query = this.getQuery(queries.get("query"));
			@SuppressWarnings("unchecked")
			final Map<String, Map<String, Object>> aliases = (Map<String, Map<String, Object>>) queries.get("aliases");
			query = this.replaceQueryVariables(query, request, parametersQr);
			result = this.manager.select(query, aliases);
			result.setMaskedColumns(mask);
		} else {
			//Pas de query principale : on simule un resultat de 1 ligne sans colonne, afin de permettre l'exécution de subqueries
			//d'extraction en masse.
			final ArrayList<QueryResult> lines = new ArrayList<>();
			result = new QueryResults(new String[] {}, lines);
			lines.add(result.new QueryResult(new Object[] {}));
		}

		result.setResultType((String) queries.get("resultType"));

		@SuppressWarnings("unchecked")
		final Map<String, Map<String, Object>> subQueries = (Map<String, Map<String, Object>>) queries.get("subqueries");
		this.fillSubQueryResults(request, result, subQueries);

		return result;
	}

	/**
	 * La query peut éventuellement être sous la forme d'un tableau, auquel cas on recolle.
	 */
	@SuppressWarnings("unchecked")
	private String getQuery(final Object query) {
		if (query instanceof String) {
			return (String) query;
		} else {
			final StringBuilder queryParts = new StringBuilder();
			((List<String>) query).forEach(e -> queryParts.append(e).append(' '));
			return queryParts.toString();
		}
	}

	/**
	 * Execute et parse toutes les sub queries puis injecte les résultat dans le résultat parent.
	 */
	private void fillSubQueryResults(final Request request, final QueryResults qr, final Map<String, Map<String, Object>> subQueries)
			throws WireMockCsvException {
		if (subQueries != null && !subQueries.isEmpty()) {
			for (final QueryResult line: qr.getLines()) {
				line.setSubResults(new HashMap<>());
				for (final Map.Entry<String, Map<String, Object>> subQuery: subQueries.entrySet()) {
					final QueryResults subResult = this.executeQueries(request, line, subQuery.getValue());
					line.getSubResults().put(subQuery.getKey(), subResult);
				}
			}
		}
	}

	/**
	 * Remplace les variables dans la requête SQL.
	 *
	 * @param querySQL
	 * @param qr
	 * @return la nouvelle requete SQL
	 */
	private String replaceQueryVariables(final String querySQL, final Request request, final QueryResult qr) {
		String newQuerySQL = querySQL;

		//En priorité, les résultats de la requête parent si présents.
		if (qr != null) {
			for (int i = 0 ; i < qr.getColumns().length ; i++) {
				newQuerySQL = newQuerySQL.replaceAll("\\$\\{" + qr.getColumns()[i] + "\\}", qr.getResult()[i].toString());
			}
		}

		//Puis les paramètres HTTP.
		for (final Map.Entry<String, ListOrSingle<String>> param: RequestTemplateModel.from(request).getQuery().entrySet()) {
			newQuerySQL = newQuerySQL.replaceAll("\\$\\{" + param.getKey() + "\\}", param.getValue().toString());
		}

		//Suppression des variables restantes non remplacées.
		newQuerySQL = newQuerySQL.replaceAll("\\$\\{[^\\}]*\\}", "");

		return newQuerySQL;
	}
}