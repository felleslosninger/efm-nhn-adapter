package no.difi.meldingsutveksling.nhn.adapter.handlers

import no.difi.meldingsutveksling.nhn.adapter.DecoratingFlrClient
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.idporten.validators.identifier.PersonIdentifierValidator
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

object ArHandlers {
    suspend fun arLookup(
        request: ServerRequest,
        flrClient: DecoratingFlrClient,
        arClient: AdresseregisteretClient,
    ): ServerResponse {
        val fnr = request.pathVariable("identifier")
        PersonIdentifierValidator.setSyntheticPersonIdentifiersAllowed(true)
        val arDetails =
            when (PersonIdentifierValidator.isValid(fnr)) {
                true -> arLookupByFnr(fnr, flrClient, arClient)
                false -> arLookupByHerId(fnr.toInt(), arClient)
            }

        return ServerResponse.ok().bodyValueAndAwait(arDetails)
    }

    private fun arLookupByFnr(
        fnr: String,
        flrClient: DecoratingFlrClient,
        arClient: AdresseregisteretClient,
    ): ArDetails {
        val gpHerId = flrClient.getPatientGP(fnr)?.gpHerId.orElseThrowNotFound("GP not found for fnr")
        return arLookupByHerId(gpHerId, arClient)
    }

    fun arLookupByHerId(herId: Int, arClient: AdresseregisteretClient): ArDetails {
        val communicationParty = arClient.lookupHerId(herId).orElseThrowNotFound("Comunication party not found in AR")
        val comunicationPartyName = communicationParty.name

        val parentHerId = communicationParty.parent?.herId.orElseThrowNotFound("HerId nivå 1 not found")
        val orgNumber = communicationParty.parent!!.organizationNumber
        val comunicationPartyParentName = communicationParty.parent?.name ?: "empty"

        return ArDetails(
            parentHerId,
            comunicationPartyParentName,
            orgNumber = orgNumber,
            herId,
            comunicationPartyName,
            "testedi-address",
            "testsertifikat",
        )
    }
}
