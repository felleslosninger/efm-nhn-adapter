package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.util.UUID

fun String.toUUID(): UUID {
    try {
        return UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("messageId must be an UUID!", e)
    }
}
