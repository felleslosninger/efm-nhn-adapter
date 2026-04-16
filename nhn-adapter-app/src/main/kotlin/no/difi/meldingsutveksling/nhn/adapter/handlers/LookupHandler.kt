package no.difi.meldingsutveksling.nhn.adapter.handlers

import no.difi.meldingsutveksling.domain.PartnerIdentifier
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
        val partnerIdentifier = PartnerIdentifier.parse(identifier)
        val communicationParty = adresseregisteretService.lookupByPartnerIdentifier(partnerIdentifier)
        val parentHerId = communicationParty.parent?.herId.orElseThrowNotFound("HerId nivå 1 not found")
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
                    "testedi-address",
                    keystoreHelper.x509Certificate.toBase64Der(),
                )
            )
    }
}
