package no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret

import jakarta.xml.ws.soap.SOAPFaultException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.nhn.adapter.handlers.HerIdNotFound
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.AdresseregisteretException
import no.ks.fiks.nhn.ar.CommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretClient

class AdresseregisteretService(
    val fastlegeregisteretClient: FastlegeregisteretClient,
    val adresseregisteretClient: AdresseregisteretClient,
) {
    suspend fun lookupByNhnIdentifier(nhnIdentifier: NhnIdentifier): CommunicationParty {
        val communicationParty =
            try {
                when (nhnIdentifier.type) {
                    NhnIdentifier.Type.FASTLEGE_FOR -> lookupByFnr(nhnIdentifier.fastlegeFor.identifier)
                    NhnIdentifier.Type.HER_ID -> lookupByHerId(nhnIdentifier.herId)
                    else -> throw HerIdNotFound()
                }
            } catch (e: AdresseregisteretApiException) {
                throw e
            } catch (e: AdresseregisteretException) {
                if (e.cause is SOAPFaultException) throw e else throw e.cause!!
            }

        return communicationParty
    }

    private suspend fun lookupByFnr(fnr: String): CommunicationParty {
        val herId = getHerIdByFnr(fnr)
        return lookupByHerId(herId)
    }

    private fun getHerIdByFnr(fnr: String): Int {
        return 8144796
        //        try {
        //            return fastlegeregisteretClient.getPatientGP(fnr)?.gpHerId ?: throw
        // HerIdNotFound()
        //        } catch (e: FastlegeregisteretApiException) {
        //            if (e.faultMessage == "ArgumentException: Personen er ikke tilknyttet
        // fastlegekontrakt") {
        //                throw HerIdNotFound()
        //            } else {
        //                throw e
        //            }
        //        } catch (e: FastlegeregisteretException) {
        //            if (e.cause is SOAPFaultException) throw e else throw e.cause!!
        //        }
    }

    suspend fun lookupByHerId(herId: Int): CommunicationParty =
        try {
            val communicationParty =
                withContext(Dispatchers.IO) { adresseregisteretClient.lookupHerId(herId) ?: throw HerIdNotFound() }

            return communicationParty
        } catch (e: AdresseregisteretApiException) {
            if ("InvalidHerIdSupplied" == e.errorCode) {
                throw HerIdNotFound()
            } else {
                throw e
            }
        } catch (e: AdresseregisteretException) {
            if (e.cause is SOAPFaultException) throw e else throw e.cause!!
        }
}
