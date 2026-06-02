package no.difi.meldingsutveksling.nhn.adapter.audit

import no.idporten.logging.audit.AuditIdentifier

enum class NHNAdapterAuditIdentifier : AuditIdentifier {
    AR_LOOKUP,
    GET_STATUS,
    SEND_APPLICATION_RECEIPT,
    SEND_MESSAGE,
    GET_MESSAGES_WITH_METADATA,
    GET_APPLICATION_RECEIPT,
    GET_BUSINESS_DOCUMENT,
    MARK_MESSAGE_AS_READ;

    override fun auditId(): String = "NHN-ADAPTER-$name"
}
