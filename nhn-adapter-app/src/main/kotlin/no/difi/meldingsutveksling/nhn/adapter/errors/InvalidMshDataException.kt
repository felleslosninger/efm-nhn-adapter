package no.difi.meldingsutveksling.nhn.adapter.errors

/**
 * Data received from MSH could not be interpreted. The fault lies with MSH, not the client, so it is reported as 502.
 */
class InvalidMshDataException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Interprets data received from MSH, reporting any failure as [InvalidMshDataException]. */
inline fun <T> fromMshData(block: () -> T): T =
    try {
        block()
    } catch (e: RuntimeException) {
        throw InvalidMshDataException("Invalid data from MSH: ${e.message}", e)
    }
