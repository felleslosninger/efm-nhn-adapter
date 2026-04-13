package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.util.*
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregister.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.logger
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.FileNames
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.SerializableApplicationReceiptInfo
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toInMessage
import no.difi.meldingsutveksling.nhn.adapter.model.toSerializable
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.ks.fiks.nhn.msh.Dialogmelding
import no.ks.fiks.nhn.msh.IncomingBusinessDocument
import no.ks.fiks.nhn.msh.IncomingVedlegg
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.util.MimeType
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

class InHandler(
    private val mshService: MshService,
    private val parcelService: ParcelService,
    private val securityService: SecurityService,
    private val adresseregisteretService: AdresseregisteretService
) {
    suspend fun getApplicationReceiptsForMessage(messageId: UUID, clientContext: ClientContext): ServerResponse {
        val incomingApplicationReceipt = mshService.getApplicationReceiptsForMessage(messageId, clientContext)
            .filter {
                securityService.hasAccess(
                    clientContext,
                    adresseregisteretService.lookupByHerId(it.receiverHerId)
                )
            }

        val json = jsonParser.encodeToString(
            ListSerializer(SerializableApplicationReceiptInfo.serializer()),
            incomingApplicationReceipt.map { it.toSerializable() }.toList(),
        )

        parcelService.signAndEncrypt(json, clientContext)

        return ServerResponse.ok()
            .contentType(MediaTypes.APPLICATION_JOSE)
            .bodyValueAndAwait(json)
    }

    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): ServerResponse {
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))
        // vi bruker status endepunkt for å hente Apprec
        val notApprecMessages =
            mshService.getMessagesWithMetadata(receiverHerId, clientContext).filter { !it.isAppRec }
        val inMessages = notApprecMessages.map { it.toInMessage() }
        val json = jsonParser.encodeToString(ListSerializer(IncomingMessage.serializer()), inMessages)

        return ServerResponse.ok()
            .contentType(MediaTypes.APPLICATION_JOSE)
            .bodyValueAndAwait(parcelService.signAndEncrypt(json, clientContext))
    }

    suspend fun getBusinessDocument(messageId: UUID, clientContext: ClientContext): ServerResponse {
        val businessDokument: IncomingBusinessDocument =
            mshService.getBusinessDocument(messageId, clientContext)
        logger.info("I was able to get the business document $businessDokument")
        val receiverHerId =
            businessDokument.receiver.parent.herId.orElseThrowNotFound("ReceiverHerId not found in SBDH!")
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))

        val multipart = MultipartBodyBuilder().apply {
            part(MultipartNames.forretningsmelding, getForretningsmelding(businessDokument, clientContext))
                .filename(FileNames.forretningsmelding)
                .contentType(MediaTypes.APPLICATION_JOSE)

            val vedlegg = ArrayList<IncomingVedlegg>()
            businessDokument.vedlegg?.let { vedlegg.add(it) }

            part(
                MultipartNames.dokumentpakke,
                getDokumentpakke(clientContext, businessDokument.message!!, vedlegg)
            )
                .filename(FileNames.dokumentpakke)
                .contentType(MediaTypes.APPLICATION_ASICE)
        }

        return ServerResponse.ok()
            .contentType(MediaType.MULTIPART_MIXED)
            .bodyValueAndAwait(multipart)
    }

    private fun getForretningsmelding(
        businessDokument: IncomingBusinessDocument,
        clientContext: ClientContext
    ): String {
        val json = jsonParser.encodeToString(businessDokument.toSerializable())
        return parcelService.signAndEncrypt(json, clientContext)
    }

    private fun getDokumentpakke(
        clientContext: ClientContext,
        dialogmelding: Dialogmelding,
        vedlegg: List<IncomingVedlegg>
    ): Resource {
        val dialogmeldingJson = jsonParser.encodeToString(dialogmelding.toSerializable())

        val attachments = ArrayList<Attachment>()
        attachments.add(
            Attachment(
                AttachmentNames.dialogmelding, ByteArrayResource(dialogmeldingJson.encodeToByteArray()),
                MimeType.valueOf("application/json")
            )
        )
        attachments.addAll(vedlegg.mapIndexed { index, vedlegg ->
            Attachment(
                AttachmentNames.vedlegg(index),
                InputStreamResource(vedlegg.data!!),
                MimeType.valueOf(vedlegg.mimeType!!)
            )
        })

        return parcelService.createAndEncryptAsic(clientContext, attachments)
    }

    suspend fun markMessageRead(messageId: UUID, receiverHerId: Int, clientContext: ClientContext): ServerResponse {
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))
        mshService.markMessageRead(messageId, receiverHerId, clientContext)

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValueAndAwait("Message deleted")
    }

}
