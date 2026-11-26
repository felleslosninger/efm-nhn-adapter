package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.swagger.v3.oas.annotations.Parameter
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.RequestTooLargeException
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.model.MshConfigurationDetails
import no.difi.meldingsutveksling.nhn.adapter.model.MshConfigurationInput
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.mshconfiguration.MshConfigurationService
import no.difi.meldingsutveksling.nhn.adapter.mshconfiguration.StoredMshConfiguration
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

/** The MSH configuration input is a few bytes; a larger body is rejected before it is held in memory. */
private const val MAX_INPUT_BYTES = 1024

/** Unlike [jsonParser], unknown keys are rejected: a misspelt clientLocked would otherwise register without a lock. */
private val strictJsonParser = Json

/**
 * The administration of the MSH configurations of the HER-ids, e.g. from MoveAdmin. Only a client with the admin scope
 * gets here, see [no.difi.meldingsutveksling.nhn.adapter.security.SecurityBeans].
 */
class MshConfigurationHandler(
    private val mshConfigurationService: MshConfigurationService,
    private val auditLogService: AuditLogService,
) {
    suspend fun list(@Parameter(hidden = true) clientContext: ClientContext): ServerResponse {
        auditLogService.log(NHNAdapterAuditIdentifier.LIST_MSH_CONFIGURATIONS.entry(clientContext)) {
            val configurations = mshConfigurationService.list().map { it.toDetails() }
            val json = jsonParser.encodeToString(ListSerializer(MshConfigurationDetails.serializer()), configurations)
            return ServerResponse.ok().json().bodyValueAndAwait(json)
        }
    }

    /** Registers the configuration of the HER-id in MSH, or replaces it. Without a body, the HER-id is not locked. */
    suspend fun register(
        herId: Int,
        request: ServerRequest,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val auditEntry =
            NHNAdapterAuditIdentifier.REGISTER_MSH_CONFIGURATION.entry(clientContext).attribute("herId", herId)

        auditLogService.log(auditEntry) {
            val input = readInput(request)
            auditEntry.attribute("clientLocked", input.clientLocked)
            val configuration = mshConfigurationService.register(herId, input.clientLocked).toDetails()
            val json = jsonParser.encodeToString(MshConfigurationDetails.serializer(), configuration)
            return ServerResponse.ok().json().bodyValueAndAwait(json)
        }
    }

    suspend fun delete(herId: Int, @Parameter(hidden = true) clientContext: ClientContext): ServerResponse {
        val auditEntry =
            NHNAdapterAuditIdentifier.DELETE_MSH_CONFIGURATION.entry(clientContext).attribute("herId", herId)

        auditLogService.log(auditEntry) {
            mshConfigurationService.delete(herId)
            return ServerResponse.ok().textPlain().bodyValueAndAwait("MSH configuration deleted")
        }
    }

    private suspend fun readInput(request: ServerRequest): MshConfigurationInput {
        val body =
            try {
                DataBufferUtils.join(request.body(BodyExtractors.toDataBuffers()), MAX_INPUT_BYTES).awaitSingleOrNull()
            } catch (_: DataBufferLimitException) {
                throw RequestTooLargeException(MAX_INPUT_BYTES.toLong())
            } ?: return MshConfigurationInput()
        val json =
            try {
                body.toString(Charsets.UTF_8)
            } finally {
                DataBufferUtils.release(body)
            }
        if (json.isBlank()) return MshConfigurationInput()
        return try {
            strictJsonParser.decodeFromString<MshConfigurationInput>(json)
        } catch (e: SerializationException) {
            throw ClientInputException("Invalid input", e)
        } catch (e: IllegalArgumentException) {
            throw ClientInputException("Invalid input", e)
        }
    }
}

private fun StoredMshConfiguration.toDetails() =
    MshConfigurationDetails(
        herId = herId,
        organizationNumber = organizationNumber,
        clientLocked = clientLocked,
        createdAt = createdAt.toOffsetDateTime(),
        updatedAt = updatedAt.toOffsetDateTime(),
        lastUsed = lastUsed?.toOffsetDateTime(),
    )

private fun Instant.toOffsetDateTime(): OffsetDateTime = atOffset(ZoneOffset.UTC)
