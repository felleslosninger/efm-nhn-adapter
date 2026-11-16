package no.difi.meldingsutveksling.nhn.adapter.errors

/**
 * The request from the client is invalid. Reported as 400.
 *
 * The message is returned to the client, so write it here and never pass on the message of a library exception: it
 * belongs in [cause]. The logs only name the class of the cause, since its message may quote the client's input (see
 * [no.difi.meldingsutveksling.nhn.adapter.logging.loggableMessage]).
 */
open class ClientInputException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
