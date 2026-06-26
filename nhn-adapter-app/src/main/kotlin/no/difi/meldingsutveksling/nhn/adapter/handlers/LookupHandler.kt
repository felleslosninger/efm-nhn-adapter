package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.swagger.v3.oas.annotations.Parameter
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.audit.clientContext
import no.difi.meldingsutveksling.nhn.adapter.extensions.toBase64Der
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.move.common.cert.KeystoreHelper
import no.idporten.logging.audit.AuditEntry
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

class LookupHandler(
    private val auditLogService: AuditLogService,
    private val adresseregisteretService: AdresseregisteretService,
    private val keystoreHelper: KeystoreHelper,
) {
    suspend fun arLookup(identifier: String, @Parameter(hidden = true) clientContext: ClientContext): ServerResponse {
        auditLogService.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.AR_LOOKUP)
                .message("Lookup of identifier")
                .clientContext(clientContext)
                .attribute("identifier", identifier)
        ) {
            val nhnIdentifier = NhnIdentifier.parse(identifier)
            val communicationParty = adresseregisteretService.lookupByNhnIdentifier(nhnIdentifier)
            val parentHerId = communicationParty.parent?.herId ?: throw HerIdNotFound()
            val orgNumber = communicationParty.parent!!.organizationNumber
            val communicationPartyParentName = communicationParty.parent?.name ?: "empty"

            return ServerResponse.ok()
                .json()
                .bodyValueAndAwait(
                    ArDetails(
                        parentHerId,
                        communicationPartyParentName,
                        orgNumber = orgNumber,
                        communicationParty.herId,
                        communicationParty.name,
                        keystoreHelper.x509Certificate.toBase64Der(),
                    )
                )
        }
    }
}
