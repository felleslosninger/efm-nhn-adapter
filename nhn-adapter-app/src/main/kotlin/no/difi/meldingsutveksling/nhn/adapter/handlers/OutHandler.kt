package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.extensions.toReceiver
import no.difi.meldingsutveksling.nhn.adapter.extensions.toSender
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregister.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.SendMessageInput
import no.difi.meldingsutveksling.nhn.adapter.model.MessageStatus
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingApplicationReceipt
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.BusinessDocumentDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toMessageStatus
import no.difi.meldingsutveksling.nhn.adapter.model.toOriginal
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.difi.move.common.dokumentpakking.domain.Document
import no.difi.move.common.io.ResourceUtils
import no.kith.xmlstds.CS
import no.kith.xmlstds.URL
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.kith.xmlstds.dialog._2013_01_23.HealthcareProfessional
import no.kith.xmlstds.dialog._2013_01_23.Notat
import no.kith.xmlstds.dialog._2013_01_23.RollerRelatertNotat
import no.kith.xmlstds.felleskomponent1.TeleCom
import no.ks.fiks.hdir.Helsepersonell
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.hdir.TemaForHelsefagligDialog
import no.ks.fiks.nhn.ar.AddressComponent
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.edi.toCV
import no.ks.fiks.nhn.msh.ConversationRef
import no.ks.fiks.nhn.msh.HttpClientException
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

class OutHandler(
    private val mshService: MshService,
    private val parcelService: ParcelService,
    private val securityService: SecurityService,
    private val adresseregisteretService: AdresseregisteretService,
) {
    suspend fun getStatus(messageId: UUID, clientContext: ClientContext): ServerResponse {
        try {
            val statuses = mshService.getStatus(messageId, clientContext).map { it.toMessageStatus() }
            return ServerResponse.ok().json().bodyValueAndAwait(statuses)
        } catch (e: HttpClientException) {
            if (e.status == 404) {
                return ServerResponse.ok().json().bodyValueAndAwait(Collections.emptyList<MessageStatus>())
            }

            throw e
        }
    }

    suspend fun sendApplicationReceipt(request: ServerRequest, clientContext: ClientContext): ServerResponse {
        val jweToken = request.awaitBody<String>()
        val payload = parcelService.decryptAndVerify(jweToken, clientContext)
        val receipt = jsonParser.decodeFromString<OutgoingApplicationReceipt>(payload)
        val messageReference = mshService.sendApplicationReceipt(receipt.toOriginal(), clientContext)
        return ServerResponse.ok().textPlain().bodyValueAndAwait(messageReference.toString())
    }

    suspend fun sendMessage(request: ServerRequest, clientContext: ClientContext): ServerResponse {
        val multipartData = request.multipartData().awaitSingle()

        val jweToken =
            multipartData
                .getFirst(MultipartNames.FORRETNINGSMELDING)!!
                .content()
                .awaitSingle()
                .toString(StandardCharsets.UTF_8)
        val payload = parcelService.decryptAndVerify(jweToken, clientContext)
        val outgoingBusinessDocument = jsonParser.decodeFromString<OutgoingBusinessDocument>(payload)
        val message = outgoingBusinessDocument.payload

        val fastlege: PersonCommunicationParty =
            adresseregisteretService.lookupByHerId(outgoingBusinessDocument.receiverHerId) as PersonCommunicationParty

        val dokumentpakke = multipartData.getFirst(MultipartNames.DOKUMENTPAKKE)!!

        val messageReference =
            withContext(Dispatchers.IO) {
                dokumentpakke.content().awaitSingle().asInputStream().use { inputStream ->
                    val asicFiles = parcelService.getAttachments(inputStream)

                    val dialogmelding = getDialogmelding(asicFiles, message.hoveddokument)
                    setDefaults(dialogmelding, fastlege)

                    val vedlegg = getVedlegg(asicFiles, message.hoveddokument)

                    val sender =
                        securityService
                            .assertAccess(
                                clientContext,
                                adresseregisteretService.lookupByHerId(outgoingBusinessDocument.senderHerId),
                            )
                            .toSender()

                    val receiver =
                        adresseregisteretService
                            .lookupByHerId(outgoingBusinessDocument.receiverHerId)
                            .toReceiver(message.pasient!!)

                    val outGoingDocument =
                        SendMessageInput(
                            UUID.fromString(outgoingBusinessDocument.messageId),
                            sender = sender,
                            receiver = receiver,
                            dialogmelding = dialogmelding,
                            vedlegg = vedlegg,
                            metadataFiler = message.metadataFiler,
                            conversationRef =
                                when {
                                    outgoingBusinessDocument.parentId != null ->
                                        ConversationRef(
                                            outgoingBusinessDocument.parentId,
                                            outgoingBusinessDocument.conversationId,
                                        )
                                    else -> null
                                },
                        )

                    mshService.sendMessage(outGoingDocument, clientContext)
                }
            }

        return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValueAndAwait(messageReference.toString())
    }

    private fun setDefaults(dialogmelding: Dialogmelding, fastlege: PersonCommunicationParty) {
        dialogmelding.notat.forEach { setDefaults(it, fastlege) }
    }

    private fun setDefaults(notat: Notat, fastlege: PersonCommunicationParty) {
        if (notat.temaKodet?.v == TemaForHelsefagligDialog.HENVENDELSE_OM_PASIENT.verdi) {
            if (notat.rollerRelatertNotat.isEmpty()) {
                notat.rollerRelatertNotat =
                    listOf(
                        toFastlegeRole(fastlege),
                        // Ved opprettelse av Helsefaglig dialog uten referanse til tidligere
                        // melding, skal rollen
                        // "Kontakt hos mottaker" oppgis med profesjonsgruppe og ev. navn på
                        // helsepersonell.
                        // Opplysningene oppgis i klassen Helsepersonell.
                        // Kravet gjelder kun ved kodeverdi 6 "Henvendelse om pasient" i elementet
                        // tema kodet.
                        RollerRelatertNotat().apply {
                            roleToPatient = HelsepersonellsFunksjoner.KONTAKT_HOS_MOTTAKER.toCV()
                            healthcareProfessional =
                                HealthcareProfessional().apply {
                                    typeHealthcareProfessional =
                                        Helsepersonell.LEGE.let {
                                            CS().apply {
                                                dn = it.navn
                                                v = it.verdi
                                            }
                                        }
                                }
                        },
                    )
            }
        }
    }

    private fun toFastlegeRole(fastlege: PersonCommunicationParty): RollerRelatertNotat =
        RollerRelatertNotat().apply {
            roleToPatient = HelsepersonellsFunksjoner.FASTLEGE.toCV()
            healthcareProfessional = toHealthcareProfessional(fastlege)
        }

    private fun toHealthcareProfessional(fastlege: PersonCommunicationParty): HealthcareProfessional =
        HealthcareProfessional().apply {
            givenName = fastlege.firstName
            middleName = fastlege.middleName
            familyName = fastlege.lastName

            fastlege.electronicAddresses
                .find { it.type == AddressComponent.TELEFONNUMMER }
                ?.address
                ?.let { phone -> teleCom.add(TeleCom().apply { teleAddress = URL().apply { v = "tel:$phone" } }) }
        }

    private fun getDialogmelding(attachments: List<Document>, hoveddokument: String): Dialogmelding {
        val document =
            attachments
                .find { it.filename == hoveddokument }
                .orElseThrowNotFound("dialogmelding.xml mangler i ASiC-E filen!")

        val xml = ResourceUtils.toString(document.resource, StandardCharsets.UTF_8)
        return BusinessDocumentDeserializer.deserializeDialogmelding(xml)
    }

    private fun getVedlegg(attachments: List<Document>, hoveddokument: String): List<Document> =
        attachments.filter { it.filename != hoveddokument }
}
