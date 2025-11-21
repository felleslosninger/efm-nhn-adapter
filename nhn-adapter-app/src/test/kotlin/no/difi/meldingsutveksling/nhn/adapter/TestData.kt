package no.difi.meldingsutveksling.nhn.adapter

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import no.difi.meldingsutveksling.nhn.adapter.model.Fagmelding
import no.difi.meldingsutveksling.nhn.adapter.model.FagmeldingRaw
import no.difi.meldingsutveksling.nhn.adapter.model.MessageOut
import no.difi.meldingsutveksling.nhn.adapter.model.Notat
import no.difi.meldingsutveksling.nhn.adapter.model.Patient
import no.difi.meldingsutveksling.nhn.adapter.model.Receiver
import no.difi.meldingsutveksling.nhn.adapter.model.Sender
import no.ks.fiks.nhn.ar.CommunicationPartyParent
import no.ks.fiks.nhn.ar.OrganizationCommunicationParty
import no.ks.fiks.nhn.ar.PersonCommunicationParty

val ON_BEHALF_OF_ORGNUM = "87362478364"
private val SENDER_DEFAULT_HERID = "234234" to "937731"
private val RECEIVER_DEFAULT_HERID = "667234" to "533739"

private const val DEFAULT_PATIENT_FNR = "234123412"
private const val DEFAULT_ON_BEHALF_OF_ORGNUM = "87362478364"

fun testFastlegeCommunicationParty(herId2: Int, herId1: Int, orgnumber: String) =
    PersonCommunicationParty(
        herId2,
        "Fastlege",
        CommunicationPartyParent(herId1, "ParentComunicationParty", orgnumber),
        listOf(),
        listOf(),
        "Peter",
        "",
        "Petterson",
    )

fun testNhnServiceCommunicationParty(herId2: Int, herId1: Int, orgnumber: String) =
    OrganizationCommunicationParty(
        herId2,
        "Nhn service",
        CommunicationPartyParent(herId1, "ParentComunicationParty", orgnumber),
        listOf(),
        listOf(),
        orgnumber,
    )

@DomeneDSL
class MessageOutBuilder() {
    @OptIn(ExperimentalUuidApi::class) var messageId: String = Uuid.random().toString()
    @OptIn(ExperimentalUuidApi::class) var conversationId: String = Uuid.random().toString()
    var onBehalfOfOrgNum: String = DEFAULT_ON_BEHALF_OF_ORGNUM

    var sender: Sender =
        Sender(herid1 = SENDER_DEFAULT_HERID.first, herid2 = SENDER_DEFAULT_HERID.second, name = "dummySender")

    var receiver: Receiver =
        Receiver(
            herid1 = RECEIVER_DEFAULT_HERID.first,
            herid2 = RECEIVER_DEFAULT_HERID.second,
            patientFnr = DEFAULT_PATIENT_FNR,
        )

    var patient: Patient =
        Patient(fnr = DEFAULT_PATIENT_FNR, firstName = "Petter", middleName = "", lastName = "Petterson")

    var fagmelding: Fagmelding =
        Fagmelding(
            notat = Notat("dummy title", "dummy body"),
            patient = patient,
            responsibleHealthcareProfessionalId = receiver.herid2,
            vedleggBeskrivelse = "dummy vedleggbeskrivelse",
        )

    /* Nested DSL sections */

    @DomeneDSL
    fun sender(block: SenderBuilder.() -> Unit) {
        sender = SenderBuilder(sender).apply(block).build()
    }

    @DomeneDSL
    fun receiver(block: ReceiverBuilder.() -> Unit) {
        receiver = ReceiverBuilder(receiver).apply(block).build()
    }

    @DomeneDSL
    fun patient(block: PatientBuilder.() -> Unit) {
        patient = PatientBuilder(patient).apply(block).build()
        // keep consistency: update Fagmelding patient if needed
        fagmelding = fagmelding.copy(patient = patient)
    }

    @DomeneDSL
    fun fagmelding(block: FagmeldingBuilder.() -> Unit) {
        fagmelding = FagmeldingBuilder(fagmelding).apply(block).build()
    }

    fun from(existing: MessageOut): MessageOutBuilder {
        messageId = existing.messageId
        conversationId = existing.conversationId
        onBehalfOfOrgNum = existing.onBehalfOfOrgNum
        sender = existing.sender
        receiver = existing.receiver

        val parsed = Json.decodeFromString<Fagmelding>(existing.fagmelding)
        patient = parsed.patient
        fagmelding = parsed

        return this
    }

    fun build(): MessageOut =
        MessageOut(
            messageId = messageId,
            conversationId = conversationId,
            onBehalfOfOrgNum = onBehalfOfOrgNum,
            sender = sender,
            receiver = receiver,
            fagmelding = Json.encodeToString(fagmelding),
        )
}

@DomeneDSL
class SenderBuilder(private val from: Sender) {

    var herid1 = from.herid1
    var herid2 = from.herid2
    var name = from.name

    fun build() = Sender(herid1, herid2, name)
}

@DomeneDSL
class ReceiverBuilder(private val from: Receiver) {

    var herid1 = from.herid1
    var herid2 = from.herid2
    var patientFnr = from.patientFnr

    fun build() = Receiver(herid1, herid2, patientFnr)
}

@DomeneDSL
class PatientBuilder(private val source: Patient) {

    var fnr = source.fnr
    var firstName = source.firstName
    var middleName = source.middleName
    var lastName = source.lastName
    var phoneNumber = source.phoneNumber

    fun build() = Patient(fnr, firstName, middleName, lastName, phoneNumber)
}

@DomeneDSL
class FagmeldingBuilder(private val source: Fagmelding) {

    var notat: Notat = source.notat
    var patient: Patient = source.patient
    var responsibleHealthcareProfessionalId: String = source.responsibleHealthcareProfessionalId
    var vedleggBeskrivelse: String = source.vedleggBeskrivelse

    @DomeneDSL
    fun notat(block: NotatBuilder.() -> Unit) {
        notat = NotatBuilder(notat).apply(block).build()
    }

    fun build(): Fagmelding =
        Fagmelding(
            notat = notat,
            patient = patient,
            responsibleHealthcareProfessionalId = responsibleHealthcareProfessionalId,
            vedleggBeskrivelse = vedleggBeskrivelse,
        )
}

@DomeneDSL
class NotatBuilder(private val source: Notat) {
    var subject = source.subject
    var notatinnhold = source.notatinnhold

    fun build() = Notat(subject, notatinnhold)
}

fun FagmeldingRaw.modify(block: TestFagmelding.() -> Unit): FagmeldingRaw =
    TestFagmelding().from(this).apply(block).let { Json {}.encodeToString(it.build()) }

class TestFagmelding() {

    var notat: Notat = Notat("dummy title", "dummy body")
    lateinit var patient: Patient
    lateinit var responsibleHealthcareProfessionalId: String
    lateinit var vedleggBeskrivelse: String

    fun from(fagmelding: FagmeldingRaw): TestFagmelding {
        val fagmelding: Fagmelding = Json {}.decodeFromString(fagmelding)
        notat = fagmelding.notat
        patient = fagmelding.patient
        responsibleHealthcareProfessionalId = fagmelding.responsibleHealthcareProfessionalId
        vedleggBeskrivelse = fagmelding.vedleggBeskrivelse
        return this
    }

    fun build(): Fagmelding = Fagmelding(notat, patient, responsibleHealthcareProfessionalId, vedleggBeskrivelse)
}

@DslMarker annotation class DomeneDSL

@DomeneDSL
fun testMessageOut(block: MessageOutBuilder.() -> Unit): MessageOut = MessageOutBuilder().apply(block).build()

@DomeneDSL fun MessageOut.modify(block: MessageOutBuilder.() -> Unit) = testMessageOut(this, block)

@DomeneDSL
fun testMessageOut(fromTemplate: MessageOut, block: MessageOutBuilder.() -> Unit): MessageOut =
    MessageOutBuilder().from(fromTemplate).apply(block).build()
