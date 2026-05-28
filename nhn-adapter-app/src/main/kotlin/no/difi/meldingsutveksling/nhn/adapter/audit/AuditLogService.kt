package no.difi.meldingsutveksling.nhn.adapter.audit

import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.idporten.logging.audit.AuditEntry
import no.idporten.logging.audit.AuditLogger

class AuditLogService(val auditLogger: AuditLogger) {
    inline fun <T> log(builder: AuditEntry.AuditEntryBuilder, action: (AuditEntry.AuditEntryBuilder) -> T): T {
        try {
            val result = action(builder)
            builder.attribute("success", true)
            return result
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
    this.clientOnbehalfofOrgno(clientContext.onBehalfOfOrgNumber)
        .clientId(clientContext.clientId)
        .clientOrgno(clientContext.orgNumber)
        .scopes(clientContext.scopes)
