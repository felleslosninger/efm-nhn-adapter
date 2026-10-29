package no.difi.meldingsutveksling.nhn.adapter.errors

/** The request from the client is invalid. Reported as 400. */
open class ClientInputException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
