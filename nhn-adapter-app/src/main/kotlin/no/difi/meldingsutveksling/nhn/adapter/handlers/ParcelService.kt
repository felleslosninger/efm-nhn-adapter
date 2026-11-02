package no.difi.meldingsutveksling.nhn.adapter.handlers

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.util.X509CertChainUtils
import java.io.InputStream
import java.security.cert.X509Certificate
import java.text.ParseException
import java.util.zip.ZipInputStream
import no.difi.asic.SignatureMethod
import no.difi.commons.asic.jaxb.asic.AsicManifest
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException
import no.difi.move.common.cert.KeystoreHelper
import no.difi.move.common.dokumentpakking.AsicParser
import no.difi.move.common.dokumentpakking.CmsAlgorithm
import no.difi.move.common.dokumentpakking.CreateCMSEncryptedAsice
import no.difi.move.common.dokumentpakking.CreateSignedJWT
import no.difi.move.common.dokumentpakking.DecryptCMSDocument
import no.difi.move.common.dokumentpakking.DecryptException
import no.difi.move.common.dokumentpakking.JsonWebEncryption
import no.difi.move.common.dokumentpakking.SignatureException
import no.difi.move.common.dokumentpakking.VerifyJWT
import no.difi.move.common.dokumentpakking.domain.AsicEAttachable
import no.difi.move.common.dokumentpakking.domain.Document
import no.difi.move.common.io.InMemoryWithTempFileFallbackResource
import no.difi.move.common.io.InMemoryWithTempFileFallbackResourceFactory
import no.difi.move.common.io.ResourceUtils
import org.bouncycastle.cms.CMSAlgorithm
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.codec.multipart.Part

/**
 * Signs, encrypts, decrypts and verifies the forretningsmelding (JOSE) and dokumentpakke (CMS encrypted ASiC-E).
 *
 * A dokumentpakke from a client may have at most [maxDokumentpakkeFiles] files, adding up to at most
 * [maxDokumentpakkeBytes] unpacked.
 */
class ParcelService(
    private val verifyJWT: VerifyJWT,
    private val asicParser: AsicParser,
    private val keystoreHelper: KeystoreHelper,
    private val decryptCMSDocument: DecryptCMSDocument,
    private val createCmsEncryptedAsice: CreateCMSEncryptedAsice,
    private val resourceFactory: InMemoryWithTempFileFallbackResourceFactory,
    private val maxDokumentpakkeFiles: Int,
    private val maxDokumentpakkeBytes: Long,
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
        return JsonWebEncryption.encrypt(signed, certificate)
    }

    /**
     * A JWE that cannot be decrypted (malformed, or not encrypted for the adapter's certificate) or a JWS that cannot
     * be verified (malformed, bad signature or invalid business certificate) is an error in the client's request.
     *
     * Validating the business certificate does not depend on the network at request time: the CRLs are cached and
     * refreshed in the background, and a CRL that cannot be downloaded is logged and skipped by the validator. A
     * validation failure is therefore always about the certificate itself, never a transient error worth retrying.
     */
    fun decryptAndVerify(jweToken: String): JWSObject {
        val privateKey = keystoreHelper.loadPrivateKey()
        return try {
            verifyJWT.verify(JsonWebEncryption.decrypt(jweToken, privateKey))
        } catch (e: DecryptException) {
            throw ClientInputException("JWE decryption failed", e)
        } catch (e: SignatureException) {
            throw ClientInputException("JWS verification failed", e)
        }
    }

    fun getSigningCertificate(jwsObject: JWSObject): X509Certificate =
        try {
            X509CertChainUtils.parse(jwsObject.header.x509CertChain).firstOrNull()
                ?: throw ClientInputException("Expected to find certificate in JWS header!")
        } catch (e: ParseException) {
            throw ClientInputException("Could not parse certificate in JWS header!", e)
        }

    /**
     * Stores the encrypted dokumentpakke and checks the size of the ASiC in it unpacked. Throws
     * [MessageTooLargeException] if it is larger than allowed.
     */
    fun receiveDokumentpakke(dokumentpakke: Part): Dokumentpakke {
        val encryptedAsic = newTempResource()
        ResourceUtils.copy(dokumentpakke.content(), encryptedAsic)

        // Decrypted each time it is read
        val asic =
            decryptCMSDocument.decrypt(
                DecryptCMSDocument.Input.builder().resource(encryptedAsic).keystoreHelper(keystoreHelper).build()
            )
        return Dokumentpakke(asic, unpackedSize(asic, maxDokumentpakkeBytes))
    }

    fun getAttachments(dokumentpakke: Dokumentpakke): List<Document> =
        LimitedAsicListener(maxDokumentpakkeFiles, maxDokumentpakkeBytes)
            .also { asicParser.parse(dokumentpakke.asic, it) }
            .documents()

    fun createAndEncryptAsic(certificate: X509Certificate, attachments: List<AsicEAttachable>): Resource =
        newTempResource().also {
            createCmsEncryptedAsice.createCmsEncryptedAsice(
                CreateCMSEncryptedAsice.Input.builder()
                    .documents(attachments.stream())
                    .certificate(certificate)
                    .signatureMethod(SignatureMethod.CAdES)
                    .signatureHelper(keystoreHelper.signatureHelper)
                    .keyEncryptionScheme(CmsAlgorithm.RSAES_OAEP)
                    .cmsEncryptionAlgorithm(CMSAlgorithm.AES256_GCM)
                    .build(),
                it,
            )
        }

    private fun newTempResource(): InMemoryWithTempFileFallbackResource =
        resourceFactory.getResource(TEMP_FILE_PREFIX, ENCRYPTED_ASIC_SUFFIX)

    private companion object {
        const val TEMP_FILE_PREFIX = "dph-"
        const val ENCRYPTED_ASIC_SUFFIX = ".asic.cms"
    }
}

/** A decrypted dokumentpakke, with the size of all its files unpacked. */
class Dokumentpakke(val asic: Resource, val unpackedSize: Long)

/**
 * Unpacks every entry of the [asic], META-INF included, without keeping it, and returns their total size. Throws
 * [MessageTooLargeException] as soon as it exceeds [maxBytes]. [AsicParser] holds each META-INF entry in memory, up to
 * 1 GiB, before any file reaches its listener.
 */
internal fun unpackedSize(asic: Resource, maxBytes: Long): Long {
    var total = 0L
    val buffer = ByteArray(8192)
    ZipInputStream(asic.inputStream).use { zip ->
        while (zip.nextEntry != null) {
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw MessageTooLargeException(
                        "The files in the dokumentpakke exceed the max size of $maxBytes bytes"
                    )
                }
            }
        }
    }
    return total
}

/**
 * Reads the files of an ASiC into memory, as [AsicParser.parse] does, but stops as soon as there are more than
 * [maxFiles] files or they add up to more than [maxBytes]. A small, compressed ASiC can otherwise unpack to more than
 * the adapter's memory.
 */
internal class LimitedAsicListener(private val maxFiles: Int, private val maxBytes: Long) : AsicParser.Listener {
    private val files = mutableListOf<Pair<String, ByteArray>>()
    private var totalBytes = 0L
    private var mimeTypes = emptyMap<String, String>()

    override fun onFile(filename: String, inputStream: InputStream) {
        if (files.size == maxFiles) {
            throw ClientInputException("The dokumentpakke has more than $maxFiles files")
        }
        // One byte more than allowed tells that the file is too large, without reading all of it
        val remainingBytes = maxBytes - totalBytes
        val content = inputStream.readNBytes((remainingBytes + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (content.size > remainingBytes) {
            throw MessageTooLargeException("The files in the dokumentpakke exceed the max size of $maxBytes bytes")
        }
        totalBytes += content.size
        files += filename to content
    }

    override fun onManifest(asicManifest: AsicManifest) {
        mimeTypes = asicManifest.file.associate { it.name to it.mimetype }
    }

    fun documents(): List<Document> =
        files.map { (filename, content) ->
            Document.builder()
                .filename(filename)
                .resource(ByteArrayResource(content))
                .mimeType(mimeTypes[filename])
                .build()
        }
}
