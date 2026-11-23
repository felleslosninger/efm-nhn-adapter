package no.difi.meldingsutveksling.nhn.adapter.errors

import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

/**
 * The business document of an incoming message is invalid, e.g. not valid XML or of a format that is not supported.
 * The fault lies with its sender, not with MSH, so it is reported as 422 with [error]: trying again does not help.
 *
 * The client rejects an invalid message with [error] in an application receipt. An invalid application receipt
 * ([isAppRec]) is never answered with another one.
 */
class InvalidIncomingDocumentException(cause: ApplicationReceiptException, val isAppRec: Boolean) :
    RuntimeException(cause.message, cause) {
    val error: FeilmeldingForApplikasjonskvittering = cause.error
}
