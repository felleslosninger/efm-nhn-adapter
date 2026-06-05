package no.difi.meldingsutveksling.nhn.adapter.extensions

import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.config.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.config.DialogmeldingMessage

val StandardBusinessDocument.messageId: String
    get() = this.messageId.castOrThrow<String>("Missing messageId!")

var StandardBusinessDocument.senderHerId: Int
    get() = this.senderIdentifier.castOrThrow<NhnIdentifier>("Missing sender!").herId
    set(value) {
        this.senderIdentifier = NhnIdentifier.herId(value)
    }

var StandardBusinessDocument.receiverHerId: Int
    get() = this.receiverIdentifier.castOrThrow<NhnIdentifier>("Missing receiver!").herId
    set(value) {
        this.receiverIdentifier = NhnIdentifier.herId(value)
    }

var StandardBusinessDocument.dialogmelding: DialogmeldingMessage
    get() = this.any.castOrThrow("Expects Dialogmelding message!")
    set(value) {
        this.any = value
    }

var StandardBusinessDocument.dialogmeldingKvittering: DialogmeldingKvitteringMessage
    get() = this.any.castOrThrow("Expects DialogmeldingKvitteringMessage message!")
    set(value) {
        this.any = value
    }

inline fun <reified T> Any?.castOrThrow(errorMessage: String = "Invalid type"): T {
    return this as? T ?: throw IllegalArgumentException(errorMessage)
}
