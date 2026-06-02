package no.difi.meldingsutveksling.nhn.adapter.security

import no.difi.meldingsutveksling.nhn.adapter.config.SecurityConfig
import no.ks.fiks.nhn.ar.CommunicationParty
import org.springframework.security.access.AccessDeniedException

class SecurityService(val securityConfig: SecurityConfig) {
    fun assertAccess(clientContext: ClientContext, communicationParty: CommunicationParty): CommunicationParty {
        assertAccess(clientContext, communicationParty.parent!!.organizationNumber)
        return communicationParty
    }

    private fun assertAccess(clientContext: ClientContext, organizationIdentifier: String) {
        if (!hasAccess(clientContext, organizationIdentifier)) {
            throw AccessDeniedException(
                "Access denied to $organizationIdentifier for ${clientContext.orgNumber} on behalf of ${clientContext.onBehalfOfOrgNumber} with delegation source = ${clientContext.delegationSource}"
            )
        }
    }

    fun hasAccess(clientContext: ClientContext, communicationParty: CommunicationParty): Boolean =
        hasAccess(clientContext, communicationParty.parent!!.organizationNumber)

    private fun hasAccess(clientContext: ClientContext, organizationIdentifier: String): Boolean {
        if (clientContext.orgNumber != clientContext.onBehalfOfOrgNumber) {
            if (clientContext.delegationSource != securityConfig.delegationSource) {
                return false
            }
        }

        return clientContext.onBehalfOfOrgNumber == organizationIdentifier
    }
}
