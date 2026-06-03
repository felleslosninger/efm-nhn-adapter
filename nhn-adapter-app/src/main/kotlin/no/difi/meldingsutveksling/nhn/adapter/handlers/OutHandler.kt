package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.audit.clientContext
import no.difi.meldingsutveksling.nhn.adapter.extensions.toJWEToken
import no.difi.meldingsutveksling.nhn.adapter.extensions.toReceiver
import no.difi.meldingsutveksling.nhn.adapter.extensions.toSender
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.SendMessageInput
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingApplicationReceipt
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.DialogmeldingDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toMessageStatus
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.difi.move.common.dokumentpakking.PartUtils
import no.difi.move.common.dokumentpakking.domain.Document
import no.difi.move.common.io.ResourceUtils
import no.idporten.logging.audit.AuditEntry
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
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

class OutHandler(
    private val mshService: MshService,
    private val parcelService: ParcelService,
    private val auditLogService: AuditLogService,
    private val securityService: SecurityService,
    private val adresseregisteretService: AdresseregisteretService,
) {
    suspend fun getStatus(messageId: UUID, clientContext: ClientContext): ServerResponse {
        auditLogService.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.GET_STATUS)
                .message("Get status")
                .clientContext(clientContext)
                .attribute("messageId", messageId)
        ) {
            val statuses = mshService.getStatus(messageId, clientContext).map { it.toMessageStatus() }
            return ServerResponse.ok().json().bodyValueAndAwait(statuses)
        }
    }

    suspend fun sendApplicationReceipt(request: ServerRequest, clientContext: ClientContext): ServerResponse {
        auditLogService.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.SEND_APPLICATION_RECEIPT)
                .message("Sending application receipt")
                .clientContext(clientContext)
        ) { auditEntryBuilder ->
            val jWSObject = parcelService.decryptAndVerify(request.toJWEToken())
            val receipt = jsonParser.decodeFromString<OutgoingApplicationReceipt>(jWSObject.payload.toString())

            auditEntryBuilder.attribute("senderHerId", receipt.senderHerId)
            auditEntryBuilder.attribute("status", receipt.payload.status)
            auditEntryBuilder.attribute("relatedToMessageId", receipt.payload.relatedToMessageId)

            val messageReference =
                withContext(Dispatchers.IO) { mshService.sendApplicationReceipt(receipt, clientContext) }

            auditEntryBuilder.attribute("messageReference", messageReference)

            return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValueAndAwait(messageReference.toString())
        }
    }

    suspend fun sendMessage(request: ServerRequest, clientContext: ClientContext): ServerResponse {
        auditLogService.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.SEND_MESSAGE)
                .message("Sending message")
                .clientContext(clientContext)
        ) { auditEntryBuilder ->
            val multipartData = request.multipartData().awaitSingle()
            val part = multipartData.getFirst(MultipartNames.FORRETNINGSMELDING)!!
            val jweToken = PartUtils.toString(part)
            val jWSObject = parcelService.decryptAndVerify(jweToken)
            val outgoingBusinessDocument =
                jsonParser.decodeFromString<OutgoingBusinessDocument>(jWSObject.payload.toString())

            auditEntryBuilder
                .attribute("messageId", outgoingBusinessDocument.messageId)
                .attribute("conversationId", outgoingBusinessDocument.conversationId)
                .attribute("parentId", outgoingBusinessDocument.parentId)
                .attribute("senderHerId", outgoingBusinessDocument.senderHerId)
                .attribute("receiverHerId", outgoingBusinessDocument.receiverHerId)

            val message = outgoingBusinessDocument.payload

            val fastlege: PersonCommunicationParty =
                adresseregisteretService.lookupByHerId(outgoingBusinessDocument.receiverHerId)
                    as PersonCommunicationParty

            val dokumentpakke = multipartData.getFirst(MultipartNames.DOKUMENTPAKKE)!!

            val messageReference =
                withContext(Dispatchers.IO) {
                    val asicFiles = parcelService.getAttachments(dokumentpakke)
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

                    val input =
                        SendMessageInput(
                            UUID.fromString(outgoingBusinessDocument.messageId),
                            sender = sender,
                            receiver = receiver,
                            dialogmelding = dialogmelding,
                            vedlegg = vedlegg,
                            metadataFiler = message.metadataFiler,
                            conversationRef =
                                when {
                                    outgoingBusinessDocument.parentId != null -> {
                                        ConversationRef(
                                            outgoingBusinessDocument.parentId,
                                            outgoingBusinessDocument.conversationId,
                                        )
                                    }
                                    else -> null
                                },
                        )

                    mshService.sendMessage(input, clientContext)
                }

            auditEntryBuilder.attribute("messageReference", messageReference)

            return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValueAndAwait(messageReference.toString())
        }
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
        val document = attachments.find { it.filename == hoveddokument } ?: throw DialogmeldingNotFound()

        val xml = ResourceUtils.toString(document.resource, StandardCharsets.UTF_8)
        return DialogmeldingDeserializer.deserializeDialogmelding(xml)
    }

    private fun getVedlegg(attachments: List<Document>, hoveddokument: String): List<Document> =
        attachments.filter { it.filename != hoveddokument }
}
