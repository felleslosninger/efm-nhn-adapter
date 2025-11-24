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
            TEST_VEDLEG_TEST_PDF,
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

private val TEST_VEDLEG_TEST_PDF: String =
    """JVBERi0xLjIgDQol4uPP0w0KIA0KOSAwIG9iag0KPDwNCi9MZW5ndGggMTAgMCBSDQovRmlsdGVyIC9GbGF0ZURlY29kZSANCj4+
DQpzdHJlYW0NCkiJzZDRSsMwFIafIO/we6eyZuckTZPtbtIWBi0UjYKQGxFbJmpliuLb26QM8X6CJBfJyf99ycmFF6xJagWrrMxz
wJeCEMd+gFjWBC1dLPeCJFkbl/fTKfwnTqt1CK0xIZyEwFYZ2T+fwT8KnmIxUmJinNKJyUiyW7mZVEQ6I54m2K3ZzFiupvgPaee7
JHFuZqyDvxuGBbZdu8D1y+7jYf+2e//C2KOJm9dxfEqqTHMRXZlR0hRJuKwZau6EJa+MOdjpYN/gprq8xVW7aRp0ZY162ySbktoW
vxpPZULGxJLSr+G4UuX+QHrcl/rz/2eqvPgGPPWhqg0KZW5kc3RyZWFtDQplbmRvYmoNCjEwIDAgb2JqDQoyNDYNCmVuZG9iag0K
NCAwIG9iag0KPDwNCi9UeXBlIC9QYWdlDQovUGFyZW50IDUgMCBSDQovUmVzb3VyY2VzIDw8DQovRm9udCA8PA0KL0YwIDYgMCBS
IA0KL0YxIDcgMCBSIA0KPj4NCi9Qcm9jU2V0IDIgMCBSDQo+Pg0KL0NvbnRlbnRzIDkgMCBSDQo+Pg0KZW5kb2JqDQo2IDAgb2Jq
DQo8PA0KL1R5cGUgL0ZvbnQNCi9TdWJ0eXBlIC9UcnVlVHlwZQ0KL05hbWUgL0YwDQovQmFzZUZvbnQgL0FyaWFsDQovRW5jb2Rp
bmcgL1dpbkFuc2lFbmNvZGluZw0KPj4NCmVuZG9iag0KNyAwIG9iag0KPDwNCi9UeXBlIC9Gb250DQovU3VidHlwZSAvVHJ1ZVR5
cGUNCi9OYW1lIC9GMQ0KL0Jhc2VGb250IC9Cb29rQW50aXF1YSxCb2xkDQovRmlyc3RDaGFyIDMxDQovTGFzdENoYXIgMjU1DQov
V2lkdGhzIFsgNzUwIDI1MCAyNzggNDAyIDYwNiA1MDAgODg5IDgzMyAyMjcgMzMzIDMzMyA0NDQgNjA2IDI1MCAzMzMgMjUwIA0K
Mjk2IDUwMCA1MDAgNTAwIDUwMCA1MDAgNTAwIDUwMCA1MDAgNTAwIDUwMCAyNTAgMjUwIDYwNiA2MDYgNjA2IA0KNDQ0IDc0NyA3
NzggNjY3IDcyMiA4MzMgNjExIDU1NiA4MzMgODMzIDM4OSAzODkgNzc4IDYxMSAxMDAwIDgzMyANCjgzMyA2MTEgODMzIDcyMiA2
MTEgNjY3IDc3OCA3NzggMTAwMCA2NjcgNjY3IDY2NyAzMzMgNjA2IDMzMyA2MDYgDQo1MDAgMzMzIDUwMCA2MTEgNDQ0IDYxMSA1
MDAgMzg5IDU1NiA2MTEgMzMzIDMzMyA2MTEgMzMzIDg4OSA2MTEgDQo1NTYgNjExIDYxMSAzODkgNDQ0IDMzMyA2MTEgNTU2IDgz
MyA1MDAgNTU2IDUwMCAzMTAgNjA2IDMxMCA2MDYgDQo3NTAgNTAwIDc1MCAzMzMgNTAwIDUwMCAxMDAwIDUwMCA1MDAgMzMzIDEw
MDAgNjExIDM4OSAxMDAwIDc1MCA3NTAgDQo3NTAgNzUwIDI3OCAyNzggNTAwIDUwMCA2MDYgNTAwIDEwMDAgMzMzIDk5OCA0NDQg
Mzg5IDgzMyA3NTAgNzUwIA0KNjY3IDI1MCAyNzggNTAwIDUwMCA2MDYgNTAwIDYwNiA1MDAgMzMzIDc0NyA0MzggNTAwIDYwNiAz
MzMgNzQ3IA0KNTAwIDQwMCA1NDkgMzYxIDM2MSAzMzMgNTc2IDY0MSAyNTAgMzMzIDM2MSA0ODggNTAwIDg4OSA4OTAgODg5IA0K
NDQ0IDc3OCA3NzggNzc4IDc3OCA3NzggNzc4IDEwMDAgNzIyIDYxMSA2MTEgNjExIDYxMSAzODkgMzg5IDM4OSANCjM4OSA4MzMg
ODMzIDgzMyA4MzMgODMzIDgzMyA4MzMgNjA2IDgzMyA3NzggNzc4IDc3OCA3NzggNjY3IDYxMSANCjYxMSA1MDAgNTAwIDUwMCA1
MDAgNTAwIDUwMCA3NzggNDQ0IDUwMCA1MDAgNTAwIDUwMCAzMzMgMzMzIDMzMyANCjMzMyA1NTYgNjExIDU1NiA1NTYgNTU2IDU1
NiA1NTYgNTQ5IDU1NiA2MTEgNjExIDYxMSA2MTEgNTU2IDYxMSANCjU1NiBdDQovRW5jb2RpbmcgL1dpbkFuc2lFbmNvZGluZw0K
L0ZvbnREZXNjcmlwdG9yIDggMCBSDQo+Pg0KZW5kb2JqDQo4IDAgb2JqDQo8PA0KL1R5cGUgL0ZvbnREZXNjcmlwdG9yDQovRm9u
dE5hbWUgL0Jvb2tBbnRpcXVhLEJvbGQNCi9GbGFncyAxNjQxOA0KL0ZvbnRCQm94IFsgLTI1MCAtMjYwIDEyMzYgOTMwIF0NCi9N
aXNzaW5nV2lkdGggNzUwDQovU3RlbVYgMTQ2DQovU3RlbUggMTQ2DQovSXRhbGljQW5nbGUgMA0KL0NhcEhlaWdodCA5MzANCi9Y
SGVpZ2h0IDY1MQ0KL0FzY2VudCA5MzANCi9EZXNjZW50IDI2MA0KL0xlYWRpbmcgMjEwDQovTWF4V2lkdGggMTAzMA0KL0F2Z1dp
ZHRoIDQ2MA0KPj4NCmVuZG9iag0KMiAwIG9iag0KWyAvUERGIC9UZXh0ICBdDQplbmRvYmoNCjUgMCBvYmoNCjw8DQovS2lkcyBb
NCAwIFIgXQ0KL0NvdW50IDENCi9UeXBlIC9QYWdlcw0KL01lZGlhQm94IFsgMCAwIDYxMiA3OTIgXQ0KPj4NCmVuZG9iag0KMSAw
IG9iag0KPDwNCi9DcmVhdG9yICgxNzI1LmZtKQ0KL0NyZWF0aW9uRGF0ZSAoMS1KYW4tMyAxODoxNVBNKQ0KL1RpdGxlICgxNzI1
LlBERikNCi9BdXRob3IgKFVua25vd24pDQovUHJvZHVjZXIgKEFjcm9iYXQgUERGV3JpdGVyIDMuMDIgZm9yIFdpbmRvd3MpDQov
S2V5d29yZHMgKCkNCi9TdWJqZWN0ICgpDQo+Pg0KZW5kb2JqDQozIDAgb2JqDQo8PA0KL1BhZ2VzIDUgMCBSDQovVHlwZSAvQ2F0
YWxvZw0KL0RlZmF1bHRHcmF5IDExIDAgUg0KL0RlZmF1bHRSR0IgIDEyIDAgUg0KPj4NCmVuZG9iag0KMTEgMCBvYmoNClsvQ2Fs
R3JheQ0KPDwNCi9XaGl0ZVBvaW50IFswLjk1MDUgMSAxLjA4OTEgXQ0KL0dhbW1hIDAuMjQ2OCANCj4+DQpdDQplbmRvYmoNCjEy
IDAgb2JqDQpbL0NhbFJHQg0KPDwNCi9XaGl0ZVBvaW50IFswLjk1MDUgMSAxLjA4OTEgXQ0KL0dhbW1hIFswLjI0NjggMC4yNDY4
IDAuMjQ2OCBdDQovTWF0cml4IFswLjQzNjEgMC4yMjI1IDAuMDEzOSAwLjM4NTEgMC43MTY5IDAuMDk3MSAwLjE0MzEgMC4wNjA2
IDAuNzE0MSBdDQo+Pg0KXQ0KZW5kb2JqDQp4cmVmDQowIDEzDQowMDAwMDAwMDAwIDY1NTM1IGYNCjAwMDAwMDIxNzIgMDAwMDAg
bg0KMDAwMDAwMjA0NiAwMDAwMCBuDQowMDAwMDAyMzYzIDAwMDAwIG4NCjAwMDAwMDAzNzUgMDAwMDAgbg0KMDAwMDAwMjA4MCAw
MDAwMCBuDQowMDAwMDAwNTE4IDAwMDAwIG4NCjAwMDAwMDA2MzMgMDAwMDAgbg0KMDAwMDAwMTc2MCAwMDAwMCBuDQowMDAwMDAw
MDIxIDAwMDAwIG4NCjAwMDAwMDAzNTIgMDAwMDAgbg0KMDAwMDAwMjQ2MCAwMDAwMCBuDQowMDAwMDAyNTQ4IDAwMDAwIG4NCnRy
YWlsZXINCjw8DQovU2l6ZSAxMw0KL1Jvb3QgMyAwIFINCi9JbmZvIDEgMCBSDQovSUQgWzw0NzE0OTUxMDQzM2RkNDg4MmYwNWY4
YzEyNDIyMzczND48NDcxNDk1MTA0MzNkZDQ4ODJmMDVmOGMxMjQyMjM3MzQ+XQ0KPj4NCnN0YXJ0eHJlZg0KMjcyNg0KJSVFT0YN
Cg=="""
