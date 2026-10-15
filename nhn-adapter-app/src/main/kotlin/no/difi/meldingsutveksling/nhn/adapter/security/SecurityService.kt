package no.difi.meldingsutveksling.nhn.adapter.security

import no.difi.meldingsutveksling.nhn.adapter.config.SecurityConfig
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.ks.fiks.nhn.ar.CommunicationParty
import org.springframework.security.access.AccessDeniedException

class SecurityService(
    private val securityConfig: SecurityConfig,
    private val adresseregisteretService: AdresseregisteretService,
) {
    /**
     * Looks up [herId] in Adresseregisteret and asserts that the client may act on behalf of the organization that
     * owns it.
     *
     * @return the communication party registered for [herId]
     * @throws AccessDeniedException if the client is not allowed to act on behalf of the owning organization
     */
    suspend fun assertAccessToHerId(clientContext: ClientContext, herId: Int): CommunicationParty =
        adresseregisteretService.lookupByHerId(herId).also { assertAccess(clientContext, it.parentOrganizationNumber) }

    private fun assertAccess(clientContext: ClientContext, organizationNumber: String) {
        if (!hasAccess(clientContext, organizationNumber)) {
            throw AccessDeniedException(
                "Access denied to $organizationNumber for ${clientContext.orgNumber} " +
                    "on behalf of ${clientContext.onBehalfOfOrgNumber} " +
                    "with delegation source = ${clientContext.delegationSource}"
            )
        }
    }

    private fun hasAccess(clientContext: ClientContext, organizationNumber: String): Boolean {
        val actsOnBehalfOfOthers = clientContext.orgNumber != clientContext.onBehalfOfOrgNumber
        if (actsOnBehalfOfOthers && clientContext.delegationSource != securityConfig.delegationSource) {
            return false
        }
        return clientContext.onBehalfOfOrgNumber == organizationNumber
    }

    private val CommunicationParty.parentOrganizationNumber: String
        get() = checkNotNull(parent) { "Communication party $herId has no parent organization" }.organizationNumber
}
