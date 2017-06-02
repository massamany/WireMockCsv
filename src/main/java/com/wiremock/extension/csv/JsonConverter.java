/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.wiremock.extension.csv.QueryResults.QueryResult;

/**
 * Convertisseur de {@link QueryResults} en JSon ou en List de Map.
 *
 */
public class JsonConverter {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final ObjectWriter INDENT_MAPPER = JsonConverter.MAPPER.writerWithDefaultPrettyPrinter();

	public JsonConverter() {
	}

	/**
	 * Formats json string
	 * @throws WireMockCsvException
	 */
	public String formatJson(final String json) throws WireMockCsvException {
		try {
			final Object obj = JsonConverter.MAPPER.readValue(json, Object.class);
			return JsonConverter.INDENT_MAPPER.writeValueAsString(obj);
		} catch (final IOException e) {
			throw new WireMockCsvException("Erreur lors de la formatage du JSON : " + e.getMessage(), e);
		}
	}

	/**
	 * Conversion d'un objet quelconque en json.
	 */
	public String convertObjectToJson(final Object object) throws WireMockCsvException {
		try {
			return JsonConverter.MAPPER.writeValueAsString(object);
		} catch (final JsonProcessingException e) {
			throw new WireMockCsvException("Erreur lors de la convertion en JSON : " + e.getMessage(), e);
		}
	}

	/**
	 * Conversion d'un json quelconque en map.
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> readJsonToMap(final File jsonFile) throws WireMockCsvException {
		try {
			return JsonConverter.MAPPER.readValue(jsonFile, Map.class);
		} catch (final JsonProcessingException e) {
			throw new WireMockCsvException("Erreur lors de la convertion en JSON : " + e.getMessage(), e);
		} catch (final IOException e) {
			throw new WireMockCsvException("Erreur lors de l'initialisation de l'extension CSV.", e);
		}
	}

	/**
	 * Conversion du {@link QueryResults} en json
	 */
	public String convertToJson(final QueryResults qr) throws WireMockCsvException {
		try {
			return JsonConverter.MAPPER.writeValueAsString(this.convert(qr));
		} catch (final JsonProcessingException e) {
			throw new WireMockCsvException("Erreur lors de la convertion en JSON : " + e.getMessage(), e);
		}
	}

	/**
	 * Conversion du {@link QueryResults} en List de Map, en Map ou en value, selon le flag "rsultType" du QueryResults.
	 */
	protected Object convert(final QueryResults qr) throws WireMockCsvException {
		final Object obj;
		if ("value".equals(qr.getResultType())) {
			if (qr.getLines().isEmpty() || qr.getLines().get(0).getResult().length == 0) {
				obj = null;
			} else {
				obj = qr.getLines().get(0).getResult()[0];
			}
		} else if ("object".equals(qr.getResultType())) {
			if (qr.getLines().isEmpty()) {
				obj = Collections.emptyMap();
			} else {
				obj = this.convertToMap(qr.getLines().get(0));
			}
		} else {
			obj = this.convertToMapList(qr);
		}
		return obj;
	}

	/**
	 * Conversion du {@link QueryResults} en List de Map
	 */
	public List<Map<String, Object>> convertToMapList(final QueryResults qr) throws WireMockCsvException {
		final List<Map<String, Object>> list = new ArrayList<>();
		for (final QueryResult line: qr.getLines()) {
			final Map<String, Object> obj = this.convertToMap(line);
			if (!obj.isEmpty()) {
				list.add(obj);
			}
		}
		return list;
	}

	/**
	 * Conversion du {@link QueryResult} en Map
	 */
	public Map<String, Object> convertToMap(final QueryResult line)
			throws WireMockCsvException {
		final Map<String, Object> obj = new LinkedHashMap<>();
		for (int i = 0; i < line.getColumns().length; i++) {
			if (! line.isMasked(line.getColumns()[i])) {
				this.addFieldToObject(obj, line.getColumns()[i].split("__"), line.getResult()[i]);
			}
		}
		if (line.getSubResults() != null) {
			for (final Map.Entry<String, QueryResults> subResult: line.getSubResults().entrySet()) {
				if (obj.containsKey(subResult.getKey())) {
					throw new WireMockCsvException("Doublon sur le champ '" + subResult.getKey() + "' lors de la convertion en JSON.");
				}
				obj.put(subResult.getKey(), this.convert(subResult.getValue()));
			}
		}
		return obj;
	}

	/**
	 * Ajoute une valeur dans une Map en fonction de son nom, déjà découpé en fonction des "__".
	 */
	@SuppressWarnings("unchecked")
	private void addFieldToObject(final Map<String, Object> obj, final String[] split, final Object object) throws WireMockCsvException {
		if (split.length == 1) {
			if (obj.containsKey(split[0])) {
				throw new WireMockCsvException("Doublon sur le champ '" + split[0] + "' lors de la convertion en JSON.");
			}
			if (object != null) {
				obj.put(split[0], object);
			}
		} else {
			//Sous objet
			Object sousObj = obj.get(split[0]);
			if (sousObj == null) {
				sousObj = new LinkedHashMap<>();
				obj.put(split[0], sousObj);
			} else if (! (sousObj instanceof Map)) {
				throw new WireMockCsvException("Doublon sur le champ '" + split[0] + "' lors de la convertion en JSON.");
			}
			final String[] sousSplit = new String[split.length - 1];
			System.arraycopy(split, 1, sousSplit, 0, split.length - 1);
			this.addFieldToObject((Map<String, Object>) sousObj, sousSplit, object);
		}
	}
}
