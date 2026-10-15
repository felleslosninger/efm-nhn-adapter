package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.swagger.v3.oas.annotations.Parameter
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.extensions.toBase64Der
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.move.common.cert.KeystoreHelper
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

class LookupHandler(
    private val auditLogService: AuditLogService,
    private val adresseregisteretService: AdresseregisteretService,
    private val keystoreHelper: KeystoreHelper,
) {
    suspend fun arLookup(identifier: String, @Parameter(hidden = true) clientContext: ClientContext): ServerResponse {
        val auditEntry = NHNAdapterAuditIdentifier.AR_LOOKUP.entry(clientContext).attribute("identifier", identifier)

        auditLogService.log(auditEntry) {
            val communicationParty = adresseregisteretService.lookupByNhnIdentifier(NhnIdentifier.parse(identifier))
            val parent = communicationParty.parent ?: throw HerIdNotFoundException()

            return ServerResponse.ok()
                .json()
                .bodyValueAndAwait(
                    ArDetails(
                        parentHerId = parent.herId,
                        parentName = parent.name,
                        orgNumber = parent.organizationNumber,
                        herId = communicationParty.herId,
                        name = communicationParty.name,
                        derCertificate = keystoreHelper.x509Certificate.toBase64Der(),
                    )
                )
        }
    }
}
