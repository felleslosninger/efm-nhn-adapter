package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.lang.IllegalArgumentException
import no.difi.meldingsutveksling.nhn.adapter.DecoratingFlrClient
import no.difi.meldingsutveksling.nhn.adapter.logger
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.idporten.validators.identifier.PersonIdentifierValidator
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretApiException
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

object ArHandlers {
    suspend fun arLookup(
        request: ServerRequest,
        flrClient: DecoratingFlrClient,
        arClient: AdresseregisteretClient,
    ): ServerResponse {
        logger.debug("I was here")
        println("I was 100% here")
        val identifier = request.pathVariable("identifier")
        PersonIdentifierValidator.setSyntheticPersonIdentifiersAllowed(true)

        val isFnr = PersonIdentifierValidator.isValid(identifier)

        if (!isFnr) {
            identifier.toIntOrNull()?.takeIf { it > 0 } ?: throw IllegalArgumentException("Illegal input")
        }

        val arDetails =
            try {
                when (PersonIdentifierValidator.isValid(identifier)) {
                    true -> arLookupByFnr(identifier, flrClient, arClient)
                    false -> arLookupByHerId(identifier.toInt(), arClient)
                }
            } catch (e: FastlegeregisteretApiException) {
                if (e.faultMessage == "ArgumentException: Personen er ikke tilknyttet fastlegekontrakt") {
                    throw HerIdNotFound()
                } else {
                    throw e
                }
            } catch (e: Exception) {
                throw e
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

    fun arLookupByHerId(herId: Int, arClient: AdresseregisteretClient): ArDetails =
        try {
            val communicationParty =
                arClient.lookupHerId(herId).orElseThrowNotFound("Comunication party not found in AR")
            val comunicationPartyName = communicationParty.name
            val parentHerId = communicationParty.parent?.herId.orElseThrowNotFound("HerId nivå 1 not found")
            val orgNumber = communicationParty.parent!!.organizationNumber
            val comunicationPartyParentName = communicationParty.parent?.name ?: "empty"

            ArDetails(
                parentHerId,
                comunicationPartyParentName,
                orgNumber = orgNumber,
                herId,
                comunicationPartyName,
                "testedi-address",
                "testsertifikat",
            )
        } catch (e: AdresseregisteretApiException) {
            if ("InvalidHerIdSupplied" == e.errorCode) {
                throw HerIdNotFound()
            } else {
                throw e
            }
        }
}
