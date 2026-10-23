package no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret

import jakarta.xml.ws.soap.SOAPFaultException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.ks.fiks.nhn.ar.rest.AddressNotFoundException
import no.ks.fiks.nhn.ar.rest.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.rest.AdresseregisteretClient
import no.ks.fiks.nhn.ar.rest.AdresseregisteretException
import no.ks.fiks.nhn.ar.rest.model.CommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretApiException
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretException

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
            } catch (_: AddressNotFoundException) {
                throw HerIdNotFoundException()
            } catch (e: AdresseregisteretApiException) {
                throw e
            } catch (e: AdresseregisteretException) {
                throw e.cause ?: e
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
     * The registry clients wrap every failure. API errors and SOAP faults are kept as is, while other failures are
     * unwrapped so that the underlying cause is reported.
     */
    private fun Exception.unwrapUnlessSoapFault(): Throwable = cause?.takeUnless { it is SOAPFaultException } ?: this
}
