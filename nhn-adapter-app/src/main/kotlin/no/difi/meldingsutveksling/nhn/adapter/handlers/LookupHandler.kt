package no.difi.meldingsutveksling.nhn.adapter.handlers

import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.nhn.adapter.extensions.toBase64Der
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregister.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.logger
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.difi.move.common.cert.KeystoreHelper
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

class LookupHandler(
    private val adresseregisteretService: AdresseregisteretService,
    private val keystoreHelper: KeystoreHelper,
) {
    suspend fun arLookup(identifier: String): ServerResponse {
        logger.info("Entering AR lookup handler")
        val nhnIdentifier = NhnIdentifier.parse(identifier)
        val communicationParty = adresseregisteretService.lookupByNhnIdentifier(nhnIdentifier)
        val parentHerId = communicationParty.parent?.herId.orElseThrowNotFound("Parent missing")
        val orgNumber = communicationParty.parent!!.organizationNumber
        val communicationPartyParentName = communicationParty.parent?.name ?: "empty"

        return ServerResponse.ok()
            .json()
            .bodyValueAndAwait(
                ArDetails(
                    parentHerId,
                    communicationPartyParentName,
                    orgNumber = orgNumber,
                    communicationParty.herId,
                    communicationParty.name,
                    keystoreHelper.x509Certificate.toBase64Der(),
                )
            )
    }
}
