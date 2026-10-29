package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException

/** Parses a UUID given by the client, where [name] identifies the value in the error message. */
fun String.toClientUuid(name: String): UUID =
    try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        throw ClientInputException("$name must be an UUID!", e)
    }
