package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

/**
 * Signals that a message cannot be processed, carrying the application receipt error code to report back.
 *
 * The message is logged, so write it here and never pass on the message of e.g. JAXB or the schema validation: it may
 * quote values from the message, such as the patient's fødselsnummer, and belongs in [cause].
 */
class ApplicationReceiptException(
    val error: FeilmeldingForApplikasjonskvittering,
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message ?: error.navn, cause)
