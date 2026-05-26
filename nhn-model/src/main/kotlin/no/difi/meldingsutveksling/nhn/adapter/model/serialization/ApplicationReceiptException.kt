package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

class ApplicationReceiptException(val error: FeilmeldingForApplikasjonskvittering, message: String? = null, throwable: Throwable? = null) :
    RuntimeException(message ?: error.navn, throwable) {
}