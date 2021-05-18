package com.wiremock.extension.csv;

import com.github.tomakehurst.wiremock.http.Body;
import com.jayway.jsonpath.JsonPath;
import com.wiremock.extension.csv.ConfigHandler.RequestConfigHandler;
import wiremock.com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WireMockJsonRequestUtils {

	private WireMockJsonRequestUtils() {
	}

	/**
	 * Remplace les variables dans la requête SQL.
	 *
	 * @param querySQL
	 * @param requestConfig
	 * @return la nouvelle requete SQL
	 */
	public static String replaceQueryVariables(final String querySQL, final RequestConfigHandler requestConfig) {
		String newQuerySQL = querySQL;

		final HashSet<String> done = new HashSet<>();
		// Standard replacement
		Matcher m = Pattern.compile("\\$\\{\\s*([^\\s^\\}]*)\\s*\\}").matcher(newQuerySQL);
		while (m.find()) {
			final String paramName = m.group(1);
			if (! done.contains(paramName)) {
				Body body = new Body(requestConfig.getRequest().getBody());
				JsonNode jsonNode = body.asJson();
				ArrayList<String> paramObject = JsonPath.read(jsonNode.toString(),paramName);
				String  paramValue = paramObject.size()>0?paramObject.get(0):"";
				newQuerySQL = newQuerySQL.replaceAll("\\$\\{\\s*" + Pattern.quote(m.group(1)) + "\\s*\\}", paramValue.toString());
				System.out.println("in newQuerySQL " + newQuerySQL);
				done.add(paramName);
			}
		}

		done.clear();

		return newQuerySQL;
	}

    /**
     * Computes files root to use between runner and csv-root-dir system property. Don't use WireMockCsvServerRunner.filesRoot() directly.
     */
    public static String getFilesRoot() {
        return System.getProperty("csv-root-dir",
                WireMockCsvServerRunner.filesRoot() == null ? "." : WireMockCsvServerRunner.filesRoot());
    }
}
