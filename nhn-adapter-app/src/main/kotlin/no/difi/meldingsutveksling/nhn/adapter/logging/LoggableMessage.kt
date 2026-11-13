package no.difi.meldingsutveksling.nhn.adapter.logging

import no.difi.meldingsutveksling.nhn.adapter.causes
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException

/**
 * The message of this exception followed by its causes, e.g. `Unknown error from MSH API; caused by ConnectException:
 * Connection refused`, with fødselsnummer masked. An exception without a message is described by its class name.
 *
 * Causes below an exception that [readsData] are described by their class name only. They are failures to read a
 * message or a request, and their messages may quote it, e.g. `Invalid NHN identifier: fastlege-for:010190-12345` from
 * [no.difi.meldingsutveksling.domain.NhnIdentifier.parse], a value from the SBD in a Jackson message, or a value from
 * the XML in a schema validation message. [maskFnr] only catches eleven digits in a row, so a fødselsnummer written
 * with a hyphen or a digit too many would get through.
 */
fun Throwable.loggableMessage(): String {
    var belowDataReader = readsData()
    val causes =
        causes().drop(1).map { cause ->
            val description = if (belowDataReader) cause.javaClass.simpleName else cause.describe()
            belowDataReader = belowDataReader || cause.readsData()
            "caused by $description"
        }
    return (sequenceOf(message ?: javaClass.simpleName) + causes).joinToString("; ").maskFnr()
}

/**
 * Whether this exception or one of its causes [readsData], so that a stack trace, which has the messages of all the
 * causes, must not be logged. Log [loggableMessage] instead.
 */
fun Throwable.hasDataInCauses(): Boolean = causes().any { it.readsData() && it.cause != null }

/**
 * The exceptions that report a failure to read a request or a message. Their own messages are written by the adapter,
 * but the messages of their causes may quote what was read.
 */
private fun Throwable.readsData(): Boolean = this is ClientInputException || this is ApplicationReceiptException

private fun Throwable.describe(): String = message?.let { "${javaClass.simpleName}: $it" } ?: javaClass.simpleName
