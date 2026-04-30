package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.io.InputStream
import java.security.cert.X509Certificate
import no.difi.asic.SignatureMethod
import no.difi.meldingsutveksling.nhn.adapter.integration.virksert.VirksertService
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.move.common.cert.KeystoreHelper
import no.difi.move.common.dokumentpakking.AsicParser
import no.difi.move.common.dokumentpakking.CmsAlgorithm
import no.difi.move.common.dokumentpakking.CreateCMSEncryptedAsice
import no.difi.move.common.dokumentpakking.DecryptCMSDocument
import no.difi.move.common.dokumentpakking.JavaWebEncryption
import no.difi.move.common.dokumentpakking.JavaWebToken
import no.difi.move.common.dokumentpakking.domain.AsicEAttachable
import no.difi.move.common.dokumentpakking.domain.Document
import no.difi.move.common.io.InMemoryWithTempFileFallbackResource
import no.difi.move.common.io.InMemoryWithTempFileFallbackResourceFactory
import no.difi.move.common.io.ResourceUtils
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.util.MimeType

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

    fun decryptAndVerify(jweToken: String, clientContext: ClientContext): String {
        val signed = JavaWebEncryption.decrypt(jweToken, keystoreHelper.loadPrivateKey())
        val certificate = certificate(clientContext)
        return JavaWebToken.verify(signed, certificate)
    }

    private fun certificate(clientContext: ClientContext): X509Certificate =
        virksertService.getCertificate(clientContext.supplier ?: clientContext.consumer)

    fun getAttachments(inputStream: InputStream): List<Document> {
        val asice =
            decryptCMSDocument.decrypt(
                DecryptCMSDocument.Input.builder()
                    .resource(InputStreamResource(inputStream))
                    .keystoreHelper(keystoreHelper)
                    .build()
            )

        return asicParser.parse(asice, this::toInMemoryWithTempFileFallbackResource)
    }

    fun toInMemoryWithTempFileFallbackResource(inputStream: InputStream): InMemoryWithTempFileFallbackResource {
        val writeableResource: InMemoryWithTempFileFallbackResource = inMemoryWithTempFileFallbackResource()
        ResourceUtils.copy(inputStream, writeableResource)
        return writeableResource
    }

    fun createAndEncryptAsic(clientContext: ClientContext, attachments: List<Attachment>): Resource {
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
