package no.difi.meldingsutveksling.nhn.adapter

import kotlin.uuid.ExperimentalUuidApi
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.crypto.Dekrypter
import no.difi.meldingsutveksling.nhn.adapter.crypto.Kryptering
import no.difi.meldingsutveksling.nhn.adapter.crypto.NhnKeystore
import no.difi.meldingsutveksling.nhn.adapter.crypto.NhnTrustStore
import no.difi.meldingsutveksling.nhn.adapter.crypto.SignatureValidator
import no.difi.meldingsutveksling.nhn.adapter.crypto.Signer
import no.difi.meldingsutveksling.nhn.adapter.crypto.toBase64Der
import no.difi.meldingsutveksling.nhn.adapter.handlers.ArHandlers
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.msh.Client
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl

val logger = KotlinLogging.logger {}

object Routes {
    const val STATUS_CHECK = "/dph/status/{messageId}"
    const val AR_LOOKUP = "/arlookup/{identifier}"
    const val INCOMING_RECEIPT = "/dph/in/{messageId}/receipt"
    const val INCOMING_MESSAGES = "/dph/in/{herId2}"
    const val INCOMING_BUSINESS_DOCUMENT = "/dph/in/{messageId}/businessDocument"
    const val DPH_OUT = "/dph/out"
    const val MARK_AS_READ = "/dph/in/{herId2}/{messageId}/markAsRead"
}

fun CoRouterFunctionDsl.statusCheck(mshClient: Client) =
    GET(Routes.STATUS_CHECK) { OutHandler.statusHandler(it, mshClient) }

fun CoRouterFunctionDsl.arLookup(
    flrClient: DecoratingFlrClient,
    arClient: AdresseregisteretClient,
    keystoreManager: NhnKeystore,
) {
    val der = keystoreManager.getPublicCertificate().toBase64Der()
    GET(Routes.AR_LOOKUP) { ArHandlers.arLookup(it, flrClient, arClient, der) }
}

fun CoRouterFunctionDsl.incomingMessages(mshClient: Client) =
    GET(Routes.INCOMING_MESSAGES) { InHandler.incomingMessages(it, mshClient) }

fun CoRouterFunctionDsl.incomingBusinessDocument(mshClient: Client) =
    GET(Routes.INCOMING_BUSINESS_DOCUMENT) { InHandler.incomingBusinessDocument(it, mshClient) }

@OptIn(ExperimentalUuidApi::class)
fun CoRouterFunctionDsl.incomingReciept(
    mshClient: Client,
    kryptering: Kryptering,
    trustStore: NhnTrustStore,
    signer: Signer,
) =
    GET(Routes.INCOMING_RECEIPT) {
        return@GET InHandler.incomingApprec(it, mshClient, kryptering, trustStore, signer)
    }

fun CoRouterFunctionDsl.dphOut(
    mshClient: Client,
    arClient: AdresseregisteretClient,
    dekryptor: Dekrypter,
    signatureValidator: SignatureValidator,
) = POST(Routes.DPH_OUT) { OutHandler.dphOut(it, arClient, mshClient, dekryptor, signatureValidator) }

fun CoRouterFunctionDsl.markAsRead(mshClient: Client, kryptering: Kryptering, signer: Signer) =
    POST(Routes.MARK_AS_READ) { InHandler.markAsRead(it, mshClient, kryptering, signer) }
