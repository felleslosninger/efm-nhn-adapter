package no.difi.meldingsutveksling.nhn.adapter.audit

import kotlin.coroutines.cancellation.CancellationException
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.idporten.logging.audit.AuditEntry
import no.idporten.logging.audit.AuditLogger

const val CANCELLED_MESSAGE = "Request was cancelled before it completed"

class AuditLogService(val auditLogger: AuditLogger) {
    /**
     * Runs [action] and writes the audit entry afterwards, recording whether the action succeeded. When the action
     * succeeds and [skipOnSuccess] returns true, nothing is logged; failures are always logged.
     *
     * Logging happens in `finally` because callers return from the enclosing function inside [action].
     *
     * A [CancellationException] means the action was cancelled before it completed, e.g. because the client went away,
     * a `withTimeout` expired or the application is shutting down. It is logged as a failure with [CANCELLED_MESSAGE],
     * since the coroutine's own message ("MonoCoroutine was cancelled") says nothing about what happened.
     */
    inline fun <T> log(
        builder: AuditEntry.AuditEntryBuilder,
        skipOnSuccess: () -> Boolean = { false },
        action: (AuditEntry.AuditEntryBuilder) -> T,
    ): T {
        var succeeded = true
        try {
            return action(builder)
        } catch (e: Throwable) {
            // Throwable, so that an Error (e.g. NotImplementedError) is not logged as a success
            succeeded = false
            builder.attribute("success", false)
            builder.attribute("exception", if (e is CancellationException) CANCELLED_MESSAGE else e.message)
            throw e
        } finally {
            if (!succeeded || !skipOnSuccess()) {
                if (succeeded) builder.attribute("success", true)
                auditLogger.log(builder.build())
            }
        }
    }
}

fun AuditEntry.AuditEntryBuilder.clientContext(clientContext: ClientContext): AuditEntry.AuditEntryBuilder =
    clientOnbehalfofOrgno(clientContext.onBehalfOfOrgNumber)
        .clientId(clientContext.clientId)
        .clientOrgno(clientContext.orgNumber)
        .scopes(clientContext.scopes)
