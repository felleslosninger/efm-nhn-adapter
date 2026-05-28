package no.difi.meldingsutveksling.nhn.adapter.handlers

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.util.X509CertChainUtils
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.text.ParseException
import no.difi.asic.SignatureMethod
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.ApplicationReceiptResponse
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.ApplicationReceiptSerializer
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentResponse
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.DialogmeldingSerializer
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.toSerializable
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.move.common.cert.KeystoreHelper
import no.difi.move.common.dokumentpakking.AsicParser
import no.difi.move.common.dokumentpakking.CmsAlgorithm
import no.difi.move.common.dokumentpakking.CreateCMSEncryptedAsice
import no.difi.move.common.dokumentpakking.CreateSignedJWT
import no.difi.move.common.dokumentpakking.DecryptCMSDocument
import no.difi.move.common.dokumentpakking.JavaWebEncryption
import no.difi.move.common.dokumentpakking.VerifyJWT
import no.difi.move.common.dokumentpakking.domain.AsicEAttachable
import no.difi.move.common.dokumentpakking.domain.Document
import no.difi.move.common.io.InMemoryWithTempFileFallbackResource
import no.difi.move.common.io.InMemoryWithTempFileFallbackResourceFactory
import no.difi.move.common.io.ResourceUtils
import org.bouncycastle.cms.CMSAlgorithm
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.Part
import org.springframework.util.MimeType

class ParcelService(
    private val verifyJWT: VerifyJWT,
    private val asicParser: AsicParser,
    private val keystoreHelper: KeystoreHelper,
    private val decryptCMSDocument: DecryptCMSDocument,
    private val createCmsEncryptedAsice: CreateCMSEncryptedAsice,
    private val resourceFactory: InMemoryWithTempFileFallbackResourceFactory,
) {
    fun signAndEncrypt(payload: String, certificate: X509Certificate): String {
        val signed =
            CreateSignedJWT.createSignedJWT(
                CreateSignedJWT.Input.builder()
                    .payload(payload)
                    .privateKey(keystoreHelper.loadPrivateKey())
                    .algorithm(JWSAlgorithm.PS256)
                    .certificate(keystoreHelper.x509Certificate)
                    .build()
            )
        return JavaWebEncryption.encrypt(signed, certificate)
    }

    fun decryptAndVerify(jweToken: String): JWSObject {
        val signed = JavaWebEncryption.decrypt(jweToken, keystoreHelper.loadPrivateKey())
        return verifyJWT.verify(signed)
    }

    fun getSigningCertificate(jwsObject: JWSObject): X509Certificate {
        try {
            return X509CertChainUtils.parse(jwsObject.header.x509CertChain).first()
                ?: throw IllegalArgumentException("Expected to find certificate in JWS header!")
        } catch (e: ParseException) {
            throw IllegalStateException("Could not parse certificate in JWS heade!", e)
        }
    }

    fun getAttachments(part: Part): List<Document> {
        val resource = resourceFactory.getResource("dph-", ".asic.cms")
        ResourceUtils.copy(part.content(), resource)

        val asice =
            decryptCMSDocument.decrypt(
                DecryptCMSDocument.Input.builder().resource(resource).keystoreHelper(keystoreHelper).build()
            )

        return asicParser.parse(asice)
    }

    fun getDokumentpakke(applicationReceipt: ApplicationReceiptResponse, certificate: X509Certificate): Resource =
        createAndEncryptAsic(
            certificate,
            listOf(
                Attachment(
                    AttachmentNames.KVITTERING,
                    ByteArrayResource(
                        ApplicationReceiptSerializer.serializeApplicationReceipt(applicationReceipt.appRec)
                            .toByteArray(StandardCharsets.UTF_8)
                    ),
                    MimeType.valueOf(MediaType.APPLICATION_XML_VALUE),
                )
            ),
        )

    fun getDokumentpakke(businessDocument: BusinessDocumentResponse, certificate: X509Certificate): Resource {
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

        return createAndEncryptAsic(certificate, attachments)
    }

    fun getForretningsmelding(businessDokument: BusinessDocumentResponse, certificate: X509Certificate): Resource {
        val json = jsonParser.encodeToString(businessDokument.toSerializable())
        val jwe = signAndEncrypt(json, certificate)
        return ByteArrayResource(jwe.toByteArray(StandardCharsets.UTF_8))
    }

    fun getForretningsmelding(applicationReceipt: ApplicationReceiptResponse, certificate: X509Certificate): Resource {
        val json = jsonParser.encodeToString(applicationReceipt.toSerializable())
        val jwe = signAndEncrypt(json, certificate)
        return ByteArrayResource(jwe.toByteArray(StandardCharsets.UTF_8))
    }

    private fun createAndEncryptAsic(certificate: X509Certificate, attachments: List<Attachment>): Resource {
        val resource: InMemoryWithTempFileFallbackResource = inMemoryWithTempFileFallbackResource()

        createCmsEncryptedAsice.createCmsEncryptedAsice(
            CreateCMSEncryptedAsice.Input.builder()
                .documents(attachments.stream())
                .certificate(certificate)
                .signatureMethod(SignatureMethod.CAdES)
                .signatureHelper(keystoreHelper.signatureHelper)
                .keyEncryptionScheme(CmsAlgorithm.RSAES_OAEP)
                .cmsEncryptionAlgorithm(CMSAlgorithm.AES256_GCM)
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
