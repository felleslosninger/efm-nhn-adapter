package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.swagger.v3.oas.annotations.Parameter
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.IdentifierPseudonymizer
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.extensions.parentOrganizationOrNull
import no.difi.meldingsutveksling.nhn.adapter.extensions.toBase64Der
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.move.common.cert.KeystoreHelper
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

private const val INVALID_IDENTIFIER_MESSAGE = "Invalid identifier. Expected her-id:<HER-id> or fastlege-for:<PID>"

class LookupHandler(
    private val auditLogService: AuditLogService,
    private val adresseregisteretService: AdresseregisteretService,
    private val keystoreHelper: KeystoreHelper,
    private val identifierPseudonymizer: IdentifierPseudonymizer,
) {
    suspend fun arLookup(identifier: String, @Parameter(hidden = true) clientContext: ClientContext): ServerResponse {
        // fastlege-for:<fnr> is a fødselsnummer, and together with the fastlege it is health data
        val auditEntry =
            NHNAdapterAuditIdentifier.AR_LOOKUP.entry(clientContext)
                .attribute("identifier", identifierPseudonymizer.pseudonymize(identifier))

        auditLogService.log(auditEntry) {
            val nhnIdentifier =
                try {
                    NhnIdentifier.parse(identifier)
                } catch (e: IllegalArgumentException) {
                    // Audit logged, so without the identifier or e.message (which has it)
                    throw ClientInputException(INVALID_IDENTIFIER_MESSAGE, e)
                }
            val communicationParty = adresseregisteretService.lookupByNhnIdentifier(nhnIdentifier)
            val parent = communicationParty.parentOrganizationOrNull ?: throw HerIdNotFoundException()

            return ServerResponse.ok()
                .json()
                .bodyValueAndAwait(
                    ArDetails(
                        parentHerId = parent.herId,
                        parentName = checkNotNull(parent.name) { "Parent organization ${parent.herId} has no name" },
                        orgNumber =
                            checkNotNull(parent.organizationNumber) {
                                "Parent organization ${parent.herId} has no organization number"
                            },
                        herId = communicationParty.herId,
                        name =
                            checkNotNull(communicationParty.name) {
                                "Communication party ${communicationParty.herId} has no name"
                            },
                        derCertificate = keystoreHelper.x509Certificate.toBase64Der(),
                    )
                )
        }
    }
}
