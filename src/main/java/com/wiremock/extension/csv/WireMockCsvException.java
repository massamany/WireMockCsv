/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

/**
 * Unique exception utilisée dans l'extension.
 *
 */
public class WireMockCsvException extends Exception {

	private static final long serialVersionUID = 1L;

	public WireMockCsvException() {
	}

	public WireMockCsvException(final String message) {
		super(message);
	}

	public WireMockCsvException(final Throwable cause) {
		super(cause);
	}

	public WireMockCsvException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public WireMockCsvException(final String message, final Throwable cause, final boolean enableSuppression,
			final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
