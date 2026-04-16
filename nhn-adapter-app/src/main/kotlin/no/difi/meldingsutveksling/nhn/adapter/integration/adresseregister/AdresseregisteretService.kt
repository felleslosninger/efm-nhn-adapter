package no.difi.meldingsutveksling.nhn.adapter.integration.adresseregister

import jakarta.xml.ws.soap.SOAPFaultException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.domain.PartnerIdentifier
import no.difi.meldingsutveksling.domain.PersonIdentifier
import no.difi.meldingsutveksling.nhn.adapter.DecoratingFlrClient
import no.difi.meldingsutveksling.nhn.adapter.handlers.HerIdNotFound
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.AdresseregisteretException
import no.ks.fiks.nhn.ar.CommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretApiException
import no.ks.fiks.nhn.flr.FastlegeregisteretException

class AdresseregisteretService(
    val flrClient: DecoratingFlrClient,
    val adresseregisteretClient: AdresseregisteretClient,
) {
    suspend fun lookupByPartnerIdentifier(partnerIdentifier: PartnerIdentifier): CommunicationParty {
        val identifier = partnerIdentifier.identifier

        val communicationParty =
            try {
                when (partnerIdentifier) {
                    is PersonIdentifier -> lookupByFnr(identifier)
                    is NhnIdentifier -> lookupByHerId(identifier.toInt())
                    else -> throw HerIdNotFound()
                }
            } catch (e: AdresseregisteretApiException) {
                throw e
            } catch (e: AdresseregisteretException) {
                if (e.cause is SOAPFaultException) throw e else throw e.cause!!
            }

        return communicationParty
    }

    private suspend fun lookupByFnr(fnr: String): CommunicationParty = lookupByHerId(getHerIdByFnr(fnr))

    private fun getHerIdByFnr(fnr: String): Int {
        try {
            return flrClient.getPatientGP(fnr)?.gpHerId.orElseThrowNotFound("GP not found for fnr")
        } catch (e: FastlegeregisteretApiException) {
            if (e.faultMessage == "ArgumentException: Personen er ikke tilknyttet fastlegekontrakt") {
                throw HerIdNotFound()
            } else {
                throw e
            }
        } catch (e: FastlegeregisteretException) {
            if (e.cause is SOAPFaultException) throw e else throw e.cause!!
        }
    }

    suspend fun lookupByHerId(herId: Int): CommunicationParty =
        try {
            val communicationParty =
                withContext(Dispatchers.IO) {
                    adresseregisteretClient
                        .lookupHerId(herId)
                        .orElseThrowNotFound("Comunication party not found in AR")
                }

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
