package no.difi.meldingsutveksling.nhn.adapter.errors

/**
 * The request from the client is invalid. Reported as 400.
 *
 * The message is returned to the client, so write it here and never pass on the message of a library exception: it
 * belongs in [cause], which is only logged.
 */
open class ClientInputException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
