package no.difi.meldingsutveksling.nhn.adapter.audit

import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.idporten.logging.audit.AuditEntry
import no.idporten.logging.audit.AuditLogger

class AuditLogService(val auditLogger: AuditLogger) {
    /** Runs [action] and writes the audit entry afterwards, recording whether the action succeeded. */
    inline fun <T> log(builder: AuditEntry.AuditEntryBuilder, action: (AuditEntry.AuditEntryBuilder) -> T): T {
        try {
            return action(builder).also { builder.attribute("success", true) }
        } catch (e: Exception) {
            builder.attribute("success", false)
            builder.attribute("exception", e.message)
            throw e
        } finally {
            auditLogger.log(builder.build())
        }
    }
}

fun AuditEntry.AuditEntryBuilder.clientContext(clientContext: ClientContext): AuditEntry.AuditEntryBuilder =
    clientOnbehalfofOrgno(clientContext.onBehalfOfOrgNumber)
        .clientId(clientContext.clientId)
        .clientOrgno(clientContext.orgNumber)
        .scopes(clientContext.scopes)
