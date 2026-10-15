package no.difi.meldingsutveksling.nhn.adapter.audit

import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.idporten.logging.audit.AuditEntry
import no.idporten.logging.audit.AuditIdentifier

enum class NHNAdapterAuditIdentifier(val message: String) : AuditIdentifier {
    AR_LOOKUP("Lookup of identifier"),
    GET_STATUS("Get status"),
    SEND_MESSAGE("Sending message"),
    GET_MESSAGES_WITH_METADATA("Get messages with metadata"),
    GET_BUSINESS_DOCUMENT("Get business document"),
    MARK_MESSAGE_AS_READ("Mark message as read");

    override fun auditId(): String = "NHN-ADAPTER-$name"

    /** Starts an audit entry for this operation, performed by the client in [clientContext]. */
    fun entry(clientContext: ClientContext): AuditEntry.AuditEntryBuilder =
        AuditEntry.builder().auditId(this).message(message).clientContext(clientContext)
}
