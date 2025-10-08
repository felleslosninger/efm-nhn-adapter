package no.difi.meldingsutveksling.nhn.adapter

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import no.difi.meldingsutveksling.nhn.adapter.model.Fagmelding
import no.difi.meldingsutveksling.nhn.adapter.model.HealthcareProfessional
import no.difi.meldingsutveksling.nhn.adapter.model.MessageOut
import no.difi.meldingsutveksling.nhn.adapter.model.Patient
import no.difi.meldingsutveksling.nhn.adapter.model.Receiver
import no.difi.meldingsutveksling.nhn.adapter.model.Sender
import no.ks.fiks.nhn.ar.CommunicationPartyParent
import no.ks.fiks.nhn.ar.OrganizationCommunicationParty
import no.ks.fiks.nhn.ar.PersonCommunicationParty

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

val SENDER_HERID: Pair<String, String> = Pair("234234", "937731")
val RECEIVER_HERID: Pair<String, String> = Pair("667234", "533739")

val HER_ID1_SENDER: String = SENDER_HERID.first
val HER_ID2_SENDER: String = SENDER_HERID.second
val HER_ID1_RECEIVER: String = RECEIVER_HERID.first
val HER_ID2_RECEIVER: String = RECEIVER_HERID.second
val PATIENT_FNR: String = "234123412"
val ON_BEHALF_OF_ORGNUM = "87362478364"
val FASTLEGE_FNR = "87893274"

@DomeneDSL
class TestMessageOut() {
    @OptIn(ExperimentalUuidApi::class) var messageId: String = Uuid.random().toString()
    @OptIn(ExperimentalUuidApi::class) var conversationId: String = Uuid.random().toString()
    var onBehalfOfOrgNum: String = ON_BEHALF_OF_ORGNUM
    var sender: Sender = TestSender().build()
    var receiver: Receiver = TestReceiver().build()
    var fagmelding: String =
        Json {}
            .encodeToString(
                Fagmelding(
                    "dummy title",
                    "dummy body",
                    HealthcareProfessional(FASTLEGE_FNR, "Jonas", "", "Jonasson", "876786"),
                )
            )
    var patient: Patient = Patient(PATIENT_FNR, "Petter", "", "Petterson", "732864827364")

    @DomeneDSL
    fun reciever(block: TestReceiver.() -> Unit) {
        this.receiver =
            TestReceiver().from(this.receiver).apply(block).let { Receiver(it.herid1, it.herid2, it.patientFnr) }
    }

    @DomeneDSL
    fun sender(block: TestSender.() -> Unit) {
        this.sender = TestSender().from(this.sender).apply(block).let { Sender(it.herid1, it.herid2, "dummyName") }
    }

    fun from(template: MessageOut): TestMessageOut {
        this.messageId = template.messageId
        this.conversationId = template.conversationId
        this.onBehalfOfOrgNum = template.onBehalfOfOrgNum
        this.sender = template.sender
        this.receiver = template.receiver
        this.fagmelding = template.fagmelding
        this.patient = template.patient
        return this
    }

    fun build(): MessageOut =
        MessageOut(
            this.messageId,
            this.conversationId,
            this.onBehalfOfOrgNum,
            this.sender,
            this.receiver,
            this.fagmelding,
            this.patient,
        )
}

@DomeneDSL
class TestSender() {
    var herid1: String = HER_ID1_SENDER
    var herid2: String = HER_ID2_SENDER
    var name: String = "dummySender"

    fun from(template: Sender): TestSender {
        this.herid1 = template.herid1
        this.herid2 = template.herid2
        this.name = template.name
        return this
    }

    fun build(): Sender = Sender(this.herid1, this.herid2, this.name)
}

@DomeneDSL
class TestReceiver() {
    var herid1: String = HER_ID1_RECEIVER
    var herid2: String = HER_ID2_RECEIVER
    var patientFnr: String? = PATIENT_FNR

    fun from(template: Receiver): TestReceiver {
        this.herid1 = template.herid1
        this.herid2 = template.herid2
        this.patientFnr = template.patientFnr
        return this
    }

    fun build(): Receiver = Receiver(this.herid1, this.herid2, this.patientFnr)
}

@DslMarker annotation class DomeneDSL

@DomeneDSL fun testMessageOut(block: TestMessageOut.() -> Unit): MessageOut = TestMessageOut().apply(block).build()

@DomeneDSL fun MessageOut.modify(block: TestMessageOut.() -> Unit) = testMessageOut(this, block)

@DomeneDSL
fun testMessageOut(fromTemplate: MessageOut, block: TestMessageOut.() -> Unit): MessageOut =
    TestMessageOut().from(fromTemplate).apply(block).build()
