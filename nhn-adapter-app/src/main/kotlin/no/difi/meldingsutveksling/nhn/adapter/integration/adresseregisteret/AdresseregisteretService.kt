package no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret

import jakarta.xml.ws.soap.SOAPFaultException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.AdresseregisteretException
import no.ks.fiks.nhn.ar.CommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretApiException
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretException

private const val INVALID_HER_ID_ERROR_CODE = "InvalidHerIdSupplied"
private const val NO_FASTLEGE_FAULT_MESSAGE = "ArgumentException: Personen er ikke tilknyttet fastlegekontrakt"

/** Looks up communication parties in Adresseregisteret, resolving a patient's fastlege via Fastlegeregisteret. */
class AdresseregisteretService(
    private val fastlegeregisteretClient: FastlegeregisteretClient,
    private val adresseregisteretClient: AdresseregisteretClient,
) {
    suspend fun lookupByNhnIdentifier(nhnIdentifier: NhnIdentifier): CommunicationParty =
        when (nhnIdentifier.type) {
            NhnIdentifier.Type.FASTLEGE_FOR -> lookupByHerId(getFastlegeHerId(nhnIdentifier.fastlegeFor.identifier))
            NhnIdentifier.Type.HER_ID -> lookupByHerId(nhnIdentifier.herId)
            else -> throw HerIdNotFoundException()
        }

    suspend fun lookupByHerId(herId: Int): CommunicationParty =
        withContext(Dispatchers.IO) {
            try {
                adresseregisteretClient.lookupHerId(herId) ?: throw HerIdNotFoundException()
            } catch (e: AdresseregisteretApiException) {
                throw if (e.errorCode == INVALID_HER_ID_ERROR_CODE) HerIdNotFoundException() else e
            } catch (e: AdresseregisteretException) {
                throw e.unwrapUnlessSoapFault()
            }
        }

    private suspend fun getFastlegeHerId(fnr: String): Int =
        withContext(Dispatchers.IO) {
            try {
                fastlegeregisteretClient.getPatientGP(fnr)?.gpHerId ?: throw HerIdNotFoundException()
            } catch (e: FastlegeregisteretApiException) {
                throw if (e.faultMessage == NO_FASTLEGE_FAULT_MESSAGE) HerIdNotFoundException() else e
            } catch (e: FastlegeregisteretException) {
                throw e.unwrapUnlessSoapFault()
            }
        }

    /**
     * The registry clients wrap every failure. SOAP faults are kept as is, while other failures are unwrapped so that
     * the underlying cause (e.g. a connection error) is reported.
     */
    private fun Exception.unwrapUnlessSoapFault(): Throwable = cause?.takeUnless { it is SOAPFaultException } ?: this
}
