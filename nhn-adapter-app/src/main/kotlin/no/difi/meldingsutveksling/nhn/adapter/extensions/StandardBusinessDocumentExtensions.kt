package no.difi.meldingsutveksling.nhn.adapter.extensions

import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingMessage

var StandardBusinessDocument.senderHerId: Int
    get() = senderIdentifier.castOrThrow<NhnIdentifier>("Missing sender!").herId
    set(value) {
        senderIdentifier = NhnIdentifier.herId(value)
    }

var StandardBusinessDocument.receiverHerId: Int
    get() = receiverIdentifier.castOrThrow<NhnIdentifier>("Missing receiver!").herId
    set(value) {
        receiverIdentifier = NhnIdentifier.herId(value)
    }

var StandardBusinessDocument.dialogmelding: DialogmeldingMessage
    get() = any.castOrThrow("Expects Dialogmelding message!")
    set(value) {
        any = value
    }

var StandardBusinessDocument.dialogmeldingKvittering: DialogmeldingKvitteringMessage
    get() = any.castOrThrow("Expects DialogmeldingKvitteringMessage message!")
    set(value) {
        any = value
    }

private inline fun <reified T> Any?.castOrThrow(errorMessage: String): T =
    this as? T ?: throw IllegalArgumentException(errorMessage)
