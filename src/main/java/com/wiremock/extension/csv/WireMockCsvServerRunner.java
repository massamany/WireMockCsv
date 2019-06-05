/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

import com.github.tomakehurst.wiremock.standalone.CommandLineOptions;
import com.github.tomakehurst.wiremock.standalone.WireMockServerRunner;

/**
 * Surcharge du lanceur, pour récupérer le filesRoot et ajouter automatiquement l'extension
 * et ainsi simplifier la ligne de commande de lancement.
 */
public class WireMockCsvServerRunner {

	private static String filesRoot;

	public static void main(final String[] args) {
		final CommandLineOptions options = new CommandLineOptions(args);
		WireMockCsvServerRunner.filesRoot = options.filesRoot().getPath();
		
		String[] args2use = null;
		for (int i = 0 ; i < args.length ; ++i) {
			if ("--extensions".equals(args[i])) {
				if (!args[i + 1].endsWith("com.wiremock.extension.csv.WireMockCsv")
						&& ! args[i + 1].contains("com.wiremock.extension.csv.WireMockCsv,")) {
					args[i + 1] += ",com.wiremock.extension.csv.WireMockCsv";
				}
				args2use = args;
			}
		}

		if (args2use == null) {
			args2use = new String[args.length + 2];
			System.arraycopy(args, 0, args2use, 0, args.length);
			args2use[args.length] = "--extensions";
			args2use[args.length + 1] = "com.wiremock.extension.csv.WireMockCsv";
		}
		WireMockServerRunner.main(args2use);
	}

	/**
	 * Internal use only. Prefer {@link WireMockCsvUtils#getFilesRoot()} instead.
	 * @return Path to files root
	 */
	public static String filesRoot() {
		return WireMockCsvServerRunner.filesRoot;
	}

}
