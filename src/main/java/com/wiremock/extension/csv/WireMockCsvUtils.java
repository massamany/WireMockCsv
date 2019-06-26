package com.wiremock.extension.csv;

import com.wiremock.extension.csv.ConfigHandler.RequestConfigHandler;

import org.apache.commons.beanutils.BeanUtilsBean;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WireMockCsvUtils {

	private WireMockCsvUtils() {
	}

	/**
	 * Remplace la variable "${WireMockCsv} dans le body final, en prenant en compte
	 * les variantes : ${WireMockCsv.subValue}, ${WireMockCsv[0].subValue}, ${WireMockCsv.subValue[0].subSubValue}, ...
	 *
	 * @param querySQL
	 * @param requestConfig
	 * @return la nouvelle requete SQL
	 */
	public static String replaceInStructure(
			JsonConverter jsonConverter, String jsonStructure, final QueryResults qr) throws WireMockCsvException {
		Object result = jsonConverter.convert(qr);
		
		// Partie 1: Remplacement de la variable WireMock sans variante.
		//TODO A optimiser : ne faire la conversion JSon que si nécessaire (via un DynamicCharSequence par exemple)
		String json = jsonConverter.convertObjectToJson(result);
		String body = Pattern.compile("\\\"?\\$\\{WireMockCsv\\}\\\"?").matcher(jsonStructure).replaceAll(Matcher.quoteReplacement(json));

		body = Pattern.compile("\\\"?\\$\\{as-string:WireMockCsv\\}\\\"?").matcher(body).replaceAll(result == null ? "" : Matcher.quoteReplacement(result.toString()));
		
		
		// Partie 2: Remplacement des variantes.
		HashMap<String, Object> holder = new HashMap<>();
		holder.put("WireMockCsv", result);
		Matcher m = Pattern.compile("\\\"?\\$\\{(WireMockCsv[\\.\\[][^\\}]*)}\\\"?").matcher(body);
		while (m.find()) {
			final String subName = m.group(1);
			try {
				Object subValue = BeanUtilsBean.getInstance().getPropertyUtils().getProperty(holder, subName);
				String subJson = jsonConverter.convertObjectToJson(subValue);
				body = body.replaceAll("\\\"?\\$\\{" + Pattern.quote(subName) + "}\\\"?", Matcher.quoteReplacement(subJson));
			} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				throw new WireMockCsvException("Erreur lors de l'accès à la propriété " + subName + '.', e);
			}
		}
		m = Pattern.compile("\\\"?\\$\\{(as-string:WireMockCsv[\\.\\[][^\\}]*)}\\\"?").matcher(body);
		while (m.find()) {
			final String subName = m.group(1);
			try {
				Object subValue = BeanUtilsBean.getInstance().getPropertyUtils().getProperty(holder, subName.replace("as-string:", ""));
				body = body.replaceAll("\\\"?\\$\\{" + Pattern.quote(subName) + "}\\\"?", Matcher.quoteReplacement(subValue == null ? "" : subValue.toString()));
			} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				throw new WireMockCsvException("Erreur lors de l'accès à la propriété " + subName + '.', e);
			}
		}

		return body;
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
				Object paramValue = requestConfig.getParamValue(paramName);
				paramValue = paramValue == null ? "" : paramValue;
				newQuerySQL = newQuerySQL.replaceAll("\\$\\{\\s*" + Pattern.quote(paramName) + "\\s*\\}", Matcher.quoteReplacement(paramValue.toString()));
				done.add(paramName);
			}
		}

		done.clear();

		// Replacement with quotes escaping
		m = Pattern.compile("\\$\\[\\s*([^\\s^\\}]*)\\s*\\]").matcher(newQuerySQL);
		while (m.find()) {
			final String paramName = m.group(1);
			if (! done.contains(paramName)) {
				Object paramValue = requestConfig.getParamValue(paramName);
				paramValue = paramValue == null ? "" : paramValue;
				newQuerySQL = newQuerySQL.replaceAll("\\$\\[\\s*" + Pattern.quote(paramName) + "\\s*\\]", Matcher.quoteReplacement(paramValue.toString().replaceAll("'", "''")));
				done.add(paramName);
			}
		}

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
