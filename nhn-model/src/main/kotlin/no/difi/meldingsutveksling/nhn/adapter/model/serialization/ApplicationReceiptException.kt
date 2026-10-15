package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

/** Signals that a message cannot be processed, carrying the application receipt error code to report back. */
class ApplicationReceiptException(
    val error: FeilmeldingForApplikasjonskvittering,
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message ?: error.navn, cause)
