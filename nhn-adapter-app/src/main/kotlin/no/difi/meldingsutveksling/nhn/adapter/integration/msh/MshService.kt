package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.HelseIdTenantParameters
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.IncomingApplicationReceipt
import no.ks.fiks.nhn.msh.IncomingBusinessDocument
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.OutgoingApplicationReceipt
import no.ks.fiks.nhn.msh.OutgoingBusinessDocument
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.StatusInfo

class MshService(val mshClient: Client) {
    suspend fun sendMessage(businessDocument: OutgoingBusinessDocument, clientContext: ClientContext): UUID =
        mshClient.sendMessage(businessDocument, getRequestParameters(clientContext))

    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): List<MessageWithMetadata> =
        mshClient.getMessagesWithMetadata(receiverHerId, getRequestParameters(clientContext))

    suspend fun getBusinessDocument(id: UUID, clientContext: ClientContext): IncomingBusinessDocument =
        mshClient.getBusinessDocument(id, getRequestParameters(clientContext))

    suspend fun getApplicationReceipt(id: UUID, clientContext: ClientContext): IncomingApplicationReceipt =
        mshClient.getApplicationReceipt(id, getRequestParameters(clientContext))

    suspend fun sendApplicationReceipt(receipt: OutgoingApplicationReceipt, clientContext: ClientContext): UUID =
        mshClient.sendApplicationReceipt(receipt, getRequestParameters(clientContext))

    suspend fun markMessageRead(id: UUID, receiverHerId: Int, clientContext: ClientContext) =
        mshClient.markMessageRead(id, receiverHerId, getRequestParameters(clientContext))

    suspend fun getStatus(id: UUID, clientContext: ClientContext): List<StatusInfo> =
        mshClient.getStatus(id, getRequestParameters(clientContext))

    private fun getRequestParameters(clientContext: ClientContext): RequestParameters =
        RequestParameters(HelseIdTokenParameters(getHelseIdTenantParameters(clientContext)))

    private fun getHelseIdTenantParameters(clientContext: ClientContext): HelseIdTenantParameters {
        val parentOrganization = clientContext.supplier?.organizationIdentifier
        val childOrganization = clientContext.consumer.organizationIdentifier
        return if (parentOrganization != null) {
            MultiTenantHelseIdTokenParameters(parentOrganization, childOrganization)
        } else {
            SingleTenantHelseIdTokenParameters(childOrganization)
        }
    }
}
