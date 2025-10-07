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

fun mockFastlegeCommunicationParty(herId2: Int, herId1: Int, orgnumber: String) =
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

fun mockNhnServiceCommunicationParty(herId2: Int, herId1: Int, orgnumber: String) =
    OrganizationCommunicationParty(
        herId2,
        "Nhn service",
        CommunicationPartyParent(herId1, "ParentComunicationParty", orgnumber),
        listOf(),
        listOf(),
        orgnumber,
    )

class MockMessageOut() {
    @OptIn(ExperimentalUuidApi::class) var messageId: String = Uuid.random().toString()
    @OptIn(ExperimentalUuidApi::class) var conversationId: String = Uuid.random().toString()
    var onBehalfOfOrgNum: String = "87362478364"
    var sender: Sender = Sender("453454", "234234", "dummySender")
    var receiver: Receiver = Receiver("23456", "332123", "dummyReceiver")
    var fagmelding: String =
        Json {}
            .encodeToString(
                Fagmelding(
                    "dummy title",
                    "dummy body",
                    HealthcareProfessional("87893274", "Jonas", "", "Jonasson", "876786"),
                )
            )
    var patient: Patient = Patient("8726428764", "Petter", "", "Petterson", "732864827364")

    fun reciever(block: MockReceiver.() -> Unit) {
        this.receiver =
            MockReceiver().from(this.receiver).apply(block).let { Receiver(it.herid1, it.herid2, it.patientFnr) }
    }

    fun sender(block: MockSender.() -> Unit) {
        this.sender = MockSender().from(this.sender).apply(block).let { Sender(it.herid1, it.herid2, "dummyName") }
    }

    fun from(template: MessageOut): MockMessageOut {
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

class MockSender() {
    var herid1: String = "234234"
    var herid2: String = "237734"
    var name: String = "dummySender"

    fun from(template: Sender): MockSender {
        this.herid1 = template.herid1
        this.herid2 = template.herid2
        this.name = template.name
        return this
    }
}

class MockReceiver() {
    var herid1: String = "234234"
    var herid2: String = "533739"
    var patientFnr: String? = "234123412"

    fun from(template: Receiver): MockReceiver {
        this.herid1 = template.herid1
        this.herid2 = template.herid2
        this.patientFnr = template.patientFnr
        return this
    }
}

fun mockMessageOut(
    senderHerdId1: String,
    senderHerId2: String,
    recieieverHerId1: String,
    receiverHerId2: String,
    block: MockMessageOut.() -> Unit,
): MockMessageOut = MockMessageOut().apply(block)

fun mockMessageOut(block: MockMessageOut.() -> Unit): MessageOut = MockMessageOut().apply(block).build()

fun mockMessageOut(fromTemplate: MessageOut, block: MockMessageOut.() -> Unit): MessageOut =
    MockMessageOut().from(fromTemplate).apply(block).build()

@OptIn(ExperimentalUuidApi::class)
fun mockMessageOut(
    senderHerdId1: String,
    senderHerId2: String,
    recieieverHerId1: String,
    receiverHerId2: String,
): MessageOut =
    MessageOut(
        Uuid.random().toString(),
        Uuid.random().toString(),
        "123213",
        Sender(senderHerdId1, senderHerId2, "Test sender"),
        Receiver(recieieverHerId1, receiverHerId2, "123456789"),
        Json {}
            .encodeToString(
                Fagmelding(
                    "subject",
                    "test body",
                    HealthcareProfessional("234234234", "Jonas", "", "Jonasson", "878767"),
                )
            ),
        Patient("123456789", "Petter", "", "Petterson", "121212"),
    )
