package no.difi.meldingsutveksling.nhn.adapter.errors

import jakarta.xml.bind.JAXBException
import kotlin.coroutines.cancellation.CancellationException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException

/**
 * Data received from MSH could not be interpreted. The fault lies with MSH, not the client, so it is reported as 502.
 */
class InvalidMshDataException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Interprets data received from MSH, reporting a failure to interpret it as [InvalidMshDataException].
 *
 * Only the failures that parsing and mapping are expected to throw are caught. Keep calls to MSH and other services
 * outside [block]: an error such as an [org.springframework.security.access.AccessDeniedException] or an MSH
 * `HttpException` passes through unchanged, but would be reported as invalid data if it were one of the caught types.
 */
inline fun <T> fromMshData(block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        when (e) {
            // A CancellationException is an IllegalStateException, but is not caused by the data
            is CancellationException -> throw e
            is IllegalArgumentException,
            is IllegalStateException,
            is NullPointerException,
            is NoSuchElementException,
            is ApplicationReceiptException,
            is JAXBException -> throw InvalidMshDataException("Invalid data from MSH: ${e.message}", e)
            else -> throw e
        }
    }
