package no.difi.meldingsutveksling.nhn.adapter.security

import no.difi.meldingsutveksling.nhn.adapter.config.SecurityConfig
import no.difi.meldingsutveksling.nhn.adapter.extensions.parentOrganization
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.ks.fiks.nhn.ar.rest.model.CommunicationParty
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

    /**
     * Asserts that the client may act on behalf of the organization it calls for, the consumer, where no HER-id tells
     * which organization the call is about. A client acting for another organization must have been delegated access
     * through the trusted delegation source.
     *
     * @throws AccessDeniedException if the client is not allowed to act on behalf of the consumer
     */
    fun assertAccessToConsumer(clientContext: ClientContext) {
        if (!isTrustedDelegation(clientContext)) {
            throw accessDenied(clientContext, clientContext.onBehalfOfOrgNumber)
        }
    }

    private fun assertAccess(clientContext: ClientContext, organizationNumber: String) {
        if (!hasAccess(clientContext, organizationNumber)) {
            throw accessDenied(clientContext, organizationNumber)
        }
    }

    private fun hasAccess(clientContext: ClientContext, organizationNumber: String): Boolean =
        isTrustedDelegation(clientContext) && clientContext.onBehalfOfOrgNumber == organizationNumber

    /** A client acting on its own behalf needs no delegation. */
    private fun isTrustedDelegation(clientContext: ClientContext): Boolean {
        val actsOnBehalfOfOthers = clientContext.orgNumber != clientContext.onBehalfOfOrgNumber
        return !actsOnBehalfOfOthers || clientContext.delegationSource == securityConfig.delegationSource
    }

    private fun accessDenied(clientContext: ClientContext, organizationNumber: String) =
        AccessDeniedException(
            "Access denied to $organizationNumber for ${clientContext.orgNumber} " +
                "on behalf of ${clientContext.onBehalfOfOrgNumber} " +
                "with delegation source = ${clientContext.delegationSource}"
        )

    private val CommunicationParty.parentOrganizationNumber: String
        get() =
            checkNotNull(parentOrganization.organizationNumber) {
                "Parent organization of communication party $herId has no organization number"
            }
}
