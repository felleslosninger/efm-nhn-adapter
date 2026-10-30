package no.difi.meldingsutveksling.nhn.adapter.handlers

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.util.X509CertChainUtils
import java.security.cert.X509Certificate
import java.text.ParseException
import no.difi.asic.SignatureMethod
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
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
import org.springframework.core.io.Resource
import org.springframework.http.codec.multipart.Part

/** Signs, encrypts, decrypts and verifies the forretningsmelding (JOSE) and dokumentpakke (CMS encrypted ASiC-E). */
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

    fun getAttachments(dokumentpakke: Part): List<Document> {
        val encryptedAsic = newTempResource()
        ResourceUtils.copy(dokumentpakke.content(), encryptedAsic)

        val asic =
            decryptCMSDocument.decrypt(
                DecryptCMSDocument.Input.builder().resource(encryptedAsic).keystoreHelper(keystoreHelper).build()
            )

        return asicParser.parse(asic)
    }

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
