package no.difi.meldingsutveksling.nhn.adapter

import kotlin.uuid.ExperimentalUuidApi
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.crypto.Dekrypter
import no.difi.meldingsutveksling.nhn.adapter.crypto.KeystoreManager
import no.difi.meldingsutveksling.nhn.adapter.crypto.toBase64Der
import no.difi.meldingsutveksling.nhn.adapter.handlers.ArHandlers
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.msh.Client
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.coRouter

val logger = KotlinLogging.logger {}

object Routes {
    const val STATUS_CHECK = "/dph/status/{messageId}"
    const val AR_LOOKUP = "/arlookup/{identifier}"
    const val INCOMING_RECEIPT = "/dph/in/{messageId}/receipt"
    const val DPH_OUT = "/dph/out"
}

fun BeanRegistrarDsl.SupplierContextDsl<RouterFunction<*>>.routes() = coRouter {
    testFlr(bean())
    testAr(bean())
    testDphOut(bean(), bean())
    testRespondApprecFralegekontor(bean())
    arLookup(bean(), bean(), bean())
    dphOut(bean(), bean(), bean())
    statusCheck(bean())
    incomingReciept(bean())
}

fun CoRouterFunctionDsl.statusCheck(mshClient: Client) =
    GET(Routes.STATUS_CHECK) { OutHandler.statusHandler(it, mshClient) }

fun CoRouterFunctionDsl.arLookup(
    flrClient: DecoratingFlrClient,
    arClient: AdresseregisteretClient,
    keystoreManager: KeystoreManager,
) {
    val der = keystoreManager.getPublicCertificate().toBase64Der()
    GET(Routes.AR_LOOKUP) { ArHandlers.arLookup(it, flrClient, arClient, der) }
}

@OptIn(ExperimentalUuidApi::class)
fun CoRouterFunctionDsl.incomingReciept(mshClient: Client) =
    GET(Routes.INCOMING_RECEIPT) {
        return@GET InHandler.incomingApprec(it, mshClient)
    }

fun CoRouterFunctionDsl.dphOut(mshClient: Client, arClient: AdresseregisteretClient, dekryptor: Dekrypter) =
    POST(Routes.DPH_OUT) { OutHandler.dphOut(it, arClient, mshClient, dekryptor) }
