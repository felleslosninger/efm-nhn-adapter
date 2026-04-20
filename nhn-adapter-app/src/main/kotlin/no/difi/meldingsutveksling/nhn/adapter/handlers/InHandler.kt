package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.extensions.jose
import no.difi.meldingsutveksling.nhn.adapter.extensions.multipartMixed
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregister.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.logger
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.model.FileNames
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
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
import org.springframework.web.reactive.function.server.json

class InHandler(
    private val mshService: MshService,
    private val parcelService: ParcelService,
    private val securityService: SecurityService,
    private val adresseregisteretService: AdresseregisteretService,
) {
    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): ServerResponse {
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))
        val inMessages = mshService.getMessagesWithMetadata(receiverHerId, clientContext).map { it.toInMessage() }
        val json = jsonParser.encodeToString(ListSerializer(IncomingMessage.serializer()), inMessages)
        return ServerResponse.ok().json().bodyValueAndAwait(json)
    }

    suspend fun getApplicationReceipt(id: UUID, clientContext: ClientContext): ServerResponse {
        val receipt = mshService.getApplicationReceipt(id, clientContext)

        // TODO: check if there is a receiverHerId in the receipt that can be used for access check.
        //        val receiverHerId = incomingApplicationReceipt.receiver.
        //        securityService.assertAccess(clientContext,
        // adresseregisteretService.lookupByHerId(receiverHerId))

        val json = jsonParser.encodeToString(receipt.toSerializable())
        val jweToken = parcelService.signAndEncrypt(json, clientContext)

        return ServerResponse.ok().jose().bodyValueAndAwait(jweToken)
    }

    suspend fun getBusinessDocument(id: UUID, clientContext: ClientContext): ServerResponse {
        val businessDokument: IncomingBusinessDocument = mshService.getBusinessDocument(id, clientContext)
        logger.info("I was able to get the business document $businessDokument")
        val receiverHerId =
            businessDokument.receiver.parent.herId.orElseThrowNotFound("ReceiverHerId not found in SBDH!")
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))

        val multipart =
            MultipartBodyBuilder().apply {
                part(MultipartNames.forretningsmelding, getForretningsmelding(businessDokument, clientContext))
                    .filename(FileNames.FORRETNINGSMELDING)
                    .contentType(MediaType.parseMediaType(ContentTypes.APPLICATION_JOSE))

                val vedlegg = ArrayList<IncomingVedlegg>()
                businessDokument.vedlegg?.let { vedlegg.add(it) }

                part(
                        MultipartNames.dokumentpakke,
                        getDokumentpakke(clientContext, businessDokument.message!!, vedlegg),
                    )
                    .filename(FileNames.DOKUMENTPAKKE)
                    .contentType(MediaType.parseMediaType(ContentTypes.APPLICATION_ASICE))
            }

        return ServerResponse.ok().multipartMixed().bodyValueAndAwait(multipart)
    }

    private fun getForretningsmelding(
        businessDokument: IncomingBusinessDocument,
        clientContext: ClientContext,
    ): String {
        val json = jsonParser.encodeToString(businessDokument.toSerializable())
        return parcelService.signAndEncrypt(json, clientContext)
    }

    private fun getDokumentpakke(
        clientContext: ClientContext,
        dialogmelding: Dialogmelding,
        vedlegg: List<IncomingVedlegg>,
    ): Resource {
        val dialogmeldingJson = jsonParser.encodeToString(dialogmelding.toSerializable())

        val attachments = ArrayList<Attachment>()
        attachments.add(
            Attachment(
                AttachmentNames.dialogmelding,
                ByteArrayResource(dialogmeldingJson.encodeToByteArray()),
                MimeType.valueOf("application/json"),
            )
        )
        attachments.addAll(
            vedlegg.mapIndexed { index, vedlegg ->
                Attachment(
                    AttachmentNames.vedlegg(index),
                    InputStreamResource(vedlegg.data!!),
                    MimeType.valueOf(vedlegg.mimeType!!),
                )
            }
        )

        return parcelService.createAndEncryptAsic(clientContext, attachments)
    }

    suspend fun markMessageRead(messageId: UUID, receiverHerId: Int, clientContext: ClientContext): ServerResponse {
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))
        mshService.markMessageRead(messageId, receiverHerId, clientContext)

        return ServerResponse.ok().textPlain().bodyValueAndAwait("Message deleted")
    }
}
