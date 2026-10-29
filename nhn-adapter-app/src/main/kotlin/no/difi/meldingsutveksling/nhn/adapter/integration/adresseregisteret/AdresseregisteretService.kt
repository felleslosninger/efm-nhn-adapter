package no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.ks.fiks.nhn.ar.rest.AddressNotFoundException
import no.ks.fiks.nhn.ar.rest.AdresseregisteretClient
import no.ks.fiks.nhn.ar.rest.model.CommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretApiException
import no.ks.fiks.nhn.flr.FastlegeregisteretClient

private const val NO_FASTLEGE_FAULT_MESSAGE = "ArgumentException: Personen er ikke tilknyttet fastlegekontrakt"

/**
 * Looks up communication parties in Adresseregisteret, resolving a patient's fastlege via Fastlegeregisteret.
 *
 * Failures other than an unknown HER-id, network errors included, are left wrapped in the registry client's exception,
 * so that they are reported as an error from that registry.
 */
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
            }
        }

    private suspend fun getFastlegeHerId(fnr: String): Int =
        withContext(Dispatchers.IO) {
            try {
                fastlegeregisteretClient.getPatientGP(fnr)?.gpHerId ?: throw HerIdNotFoundException()
            } catch (e: FastlegeregisteretApiException) {
                throw if (e.faultMessage == NO_FASTLEGE_FAULT_MESSAGE) HerIdNotFoundException() else e
            }
        }
}
