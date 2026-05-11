package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import no.difi.asic.SignatureMethod
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.ApplicationReceiptResponse
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentResponse
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.DialogmeldingSerializer
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.toSerializable
import no.difi.meldingsutveksling.nhn.adapter.integration.virksert.VirksertService
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.move.common.cert.KeystoreHelper
import no.difi.move.common.dokumentpakking.AsicParser
import no.difi.move.common.dokumentpakking.CmsAlgorithm
import no.difi.move.common.dokumentpakking.CreateCMSEncryptedAsice
import no.difi.move.common.dokumentpakking.DecryptCMSDocument
import no.difi.move.common.dokumentpakking.JavaWebEncryption
import no.difi.move.common.dokumentpakking.JavaWebToken
import no.difi.move.common.dokumentpakking.PartUtils
import no.difi.move.common.dokumentpakking.domain.AsicEAttachable
import no.difi.move.common.dokumentpakking.domain.Document
import no.difi.move.common.io.InMemoryWithTempFileFallbackResource
import no.difi.move.common.io.InMemoryWithTempFileFallbackResourceFactory
import no.difi.move.common.io.ResourceUtils
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.Part
import org.springframework.util.MimeType
import org.springframework.util.MultiValueMap

class ParcelService(
    private val asicParser: AsicParser,
    private val keystoreHelper: KeystoreHelper,
    private val virksertService: VirksertService,
    private val decryptCMSDocument: DecryptCMSDocument,
    private val createCmsEncryptedAsice: CreateCMSEncryptedAsice,
    private val resourceFactory: InMemoryWithTempFileFallbackResourceFactory,
) {
    fun signAndEncrypt(payload: String, clientContext: ClientContext): String {
        val signed = JavaWebToken.sign(payload, keystoreHelper.loadPrivateKey())
        return JavaWebEncryption.encrypt(signed, certificate(clientContext))
    }

    fun getOutgoingBusinessDocument(
        multipartData: MultiValueMap<String, Part>,
        clientContext: ClientContext,
    ): OutgoingBusinessDocument {
        val payload = getPayload(multipartData, clientContext)
        return jsonParser.decodeFromString<OutgoingBusinessDocument>(payload)
    }

    private fun getPayload(multipartData: MultiValueMap<String, Part>, clientContext: ClientContext): String {
        val part = multipartData.getFirst(MultipartNames.FORRETNINGSMELDING)!!
        val jweToken = PartUtils.toString(part)
        return decryptAndVerify(jweToken, clientContext)
    }

    private fun decryptAndVerify(jweToken: String, clientContext: ClientContext): String {
        val signed = JavaWebEncryption.decrypt(jweToken, keystoreHelper.loadPrivateKey())
        val certificate = certificate(clientContext)
        return JavaWebToken.verify(signed, certificate)
    }

    private fun certificate(clientContext: ClientContext): X509Certificate =
        virksertService.getCertificate(clientContext.supplier ?: clientContext.consumer)

    fun getAttachments(part: Part): List<Document> {
        val resource = resourceFactory.getResource("dph-", ".asic.cms")
        ResourceUtils.copy(part.content(), resource)

        val asice =
            decryptCMSDocument.decrypt(
                DecryptCMSDocument.Input.builder().resource(resource).keystoreHelper(keystoreHelper).build()
            )

        return asicParser.parse(asice)
    }

    fun getDokumentpakke(applicationReceipt: ApplicationReceiptResponse, clientContext: ClientContext): Resource {
        val attachments = ArrayList<Attachment>()
        attachments.add(
            Attachment(
                AttachmentNames.KVITTERING,
                ByteArrayResource(applicationReceipt.rawReceipt.encodeToByteArray()),
                MimeType.valueOf(MediaType.APPLICATION_XML_VALUE),
            )
        )

        return createAndEncryptAsic(clientContext, attachments)
    }

    fun getDokumentpakke(businessDocument: BusinessDocumentResponse, clientContext: ClientContext): Resource {
        val xml = DialogmeldingSerializer.serializeDialogmelding(businessDocument.dialogmelding)
        val attachments = ArrayList<Attachment>()
        attachments.add(
            Attachment(
                AttachmentNames.DIALOGMELDING,
                ByteArrayResource(xml.encodeToByteArray()),
                MimeType.valueOf(MediaType.APPLICATION_XML_VALUE),
            )
        )
        attachments.addAll(
            businessDocument.attachments.mapIndexed { index, attachment ->
                Attachment(
                    AttachmentNames.vedlegg(index, attachment.mimeType),
                    InputStreamResource(attachment.data!!),
                    MimeType.valueOf(attachment.mimeType),
                )
            }
        )

        return createAndEncryptAsic(clientContext, attachments)
    }

    fun getForretningsmelding(businessDokument: BusinessDocumentResponse, clientContext: ClientContext): Resource {
        val json = jsonParser.encodeToString(businessDokument.toSerializable())
        val jwe = signAndEncrypt(json, clientContext)
        return ByteArrayResource(jwe.toByteArray(StandardCharsets.UTF_8))
    }

    fun getForretningsmelding(applicationReceipt: ApplicationReceiptResponse, clientContext: ClientContext): Resource {
        val json = jsonParser.encodeToString(applicationReceipt.toSerializable())
        val jwe = signAndEncrypt(json, clientContext)
        return ByteArrayResource(jwe.toByteArray(StandardCharsets.UTF_8))
    }

    private fun createAndEncryptAsic(clientContext: ClientContext, attachments: List<Attachment>): Resource {
        val resource: InMemoryWithTempFileFallbackResource = inMemoryWithTempFileFallbackResource()

        createCmsEncryptedAsice.createCmsEncryptedAsice(
            CreateCMSEncryptedAsice.Input.builder()
                .documents(attachments.stream())
                .certificate(certificate(clientContext))
                .signatureMethod(SignatureMethod.CAdES)
                .signatureHelper(keystoreHelper.signatureHelper)
                .keyEncryptionScheme(CmsAlgorithm.RSAES_OAEP)
                .build(),
            resource,
        )

        return resource
    }

    private fun inMemoryWithTempFileFallbackResource(): InMemoryWithTempFileFallbackResource =
        resourceFactory.getResource("dph-", ".asic.cms")
}

data class Attachment(private val filename: String, private val resource: Resource, private val mimeType: MimeType) :
    AsicEAttachable {
    override fun getFilename(): String = filename

    override fun getResource(): Resource = resource

    override fun getMimeType(): MimeType = mimeType
}
