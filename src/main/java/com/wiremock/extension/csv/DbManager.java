/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Gestionnaire minimaliste de connexion à la base CSV.
 *
 */
public class DbManager {

	private final String csvPath;
	private Connection dbConnection;

	/**
	 * Constructor
	 * @param csvPath Path to csv files directory
	 */
	public DbManager(final String csvPath) {
		this.csvPath = csvPath;
	}

	/**
	 * Connexion a la base de donnée
	 *
	 * @throws WireMockCsvException When a technical exception occurs
	 */
	public void dbConnect() throws WireMockCsvException {
		try {
			Class.forName("org.hsqldb.jdbcDriver").newInstance();
			this.dbConnection = DriverManager.getConnection("jdbc:hsqldb:file:" + this.csvPath, "CSV", "");
		} catch (final InstantiationException | IllegalAccessException | ClassNotFoundException | SQLException e) {
			throw new WireMockCsvException("Erreur lors de la création de la base de données CSV : " + e.getMessage(), e);
		}
	}

	/**
	 * Deconnexion de la base de donnée
	 *
	 * @throws WireMockCsvException When a technical exception occurs
	 */
	public void dbDisconnect() throws WireMockCsvException {
		try {
			final Statement statement = this.dbConnection.createStatement();
			statement.execute("SHUTDOWN");
			statement.close();
			this.dbConnection.close();
		} catch (final SQLException e) {
			throw new WireMockCsvException("Erreur lors de la déconnexion de la base de données CSV : " + e.getMessage(), e);
		}

	}

	/**
	 * Fonction permettant d'exécuter la requete SQL de lecture dont le résultat sera retourné.
	 *
	 * @param querySQL SQL Query to execute
	 * @param aliases Columns names aliases
	 * @return Query execution results
	 * 
	 * @throws WireMockCsvException When a technical exception occurs
	 */
	public QueryResults select(final String querySQL, final Map<String, Map<String, Object>> aliases) throws WireMockCsvException {
		try (final Statement stmt = this.dbConnection.createStatement();
				final ResultSet results = stmt.executeQuery(querySQL)) {
			return new QueryResults(results, aliases);
		} catch (final SQLException e) {
			throw new WireMockCsvException("Erreur lors du select dans la base de données CSV pour la requête " + querySQL + " : " + e.getMessage(), e);
		}
	}

}
