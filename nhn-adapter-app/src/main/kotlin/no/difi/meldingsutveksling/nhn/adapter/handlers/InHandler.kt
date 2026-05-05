package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.extensions.jose
import no.difi.meldingsutveksling.nhn.adapter.extensions.multipartMixed
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregister.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentSerializer
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.logger
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.BusinessDocumentResponse
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartFileNames
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toInMessage
import no.difi.meldingsutveksling.nhn.adapter.model.toSerializable
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
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
        val businessDocument: BusinessDocumentResponse = mshService.getBusinessDocument(id, clientContext)
        logger.info("I was able to get the business document $businessDocument")
        val receiverHerId =
            businessDocument.receiver.child.herId.orElseThrowNotFound("ReceiverHerId not found in SBDH!")
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))

        val multipart =
            MultipartBodyBuilder()
                .apply {
                    part(
                            MultipartNames.FORRETNINGSMELDING,
                            getForretningsmelding(businessDocument, clientContext),
                            MediaType.parseMediaType(ContentTypes.APPLICATION_JOSE),
                        )
                        .filename(MultipartFileNames.FORRETNINGSMELDING)

                    part(
                            MultipartNames.DOKUMENTPAKKE,
                            getDokumentpakke(businessDocument, clientContext),
                            MediaType.parseMediaType(ContentTypes.APPLICATION_ASICE),
                        )
                        .filename(MultipartFileNames.DOKUMENTPAKKE)
                }
                .build()

        return ServerResponse.ok().multipartMixed().bodyValueAndAwait(multipart)
    }

    private fun getForretningsmelding(
        businessDokument: BusinessDocumentResponse,
        clientContext: ClientContext,
    ): Resource {
        val json = jsonParser.encodeToString(businessDokument.toSerializable())
        val jwe = parcelService.signAndEncrypt(json, clientContext)
        return ByteArrayResource(jwe.toByteArray(StandardCharsets.UTF_8))
    }

    private fun getDokumentpakke(businessDocument: BusinessDocumentResponse, clientContext: ClientContext): Resource {
        val xml = BusinessDocumentSerializer.serialize(businessDocument.dialogmelding)
        val attachments = ArrayList<Attachment>()
        attachments.add(
            Attachment(
                AttachmentNames.DIALOGMELDING,
                ByteArrayResource(xml.encodeToByteArray()),
                MimeType.valueOf("application/json"),
            )
        )
        attachments.addAll(
            businessDocument.vedlegg.mapIndexed { index, vedlegg ->
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
