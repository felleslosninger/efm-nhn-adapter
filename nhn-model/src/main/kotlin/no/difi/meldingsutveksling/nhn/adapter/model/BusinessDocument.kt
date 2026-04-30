package no.difi.meldingsutveksling.nhn.adapter.model

//import no.ks.fiks.nhn.msh.OrganizationCommunicationParty
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable

@Serializable
data class OutgoingBusinessDocument(
    val messageId: String,
    val conversationId: String?,
    val parentId: String?,
    val senderHerId: Int,
    val receiverHerId: Int,
    val payload: DialogmeldingMessage
)

@Serializable
data class IncomingBusinessDocument(
    val id: String,
    val senderHerId: Int,
    val receiverHerId: Int,
    val conversationId: String?,
    val parentId: String?,
    val payload: DialogmeldingMessage
)

@Serializable
data class DialogmeldingMessage(
    val hoveddokument: String,
    val pasient: Pasient?,
    val metadataFiler: Map<String, String>
)

@Serializable
data class Pasient(val fnr: String, val fornavn: String, val mellomnavn: String?, val etternavn: String)


@Serializable
data class Dialogmelding(
    val foresporsel: Foresporsel? = null,
    val notat: Notat?
)

@Serializable
data class Foresporsel(
    val sporsmal: String?,
)

@Serializable
data class Notat(
    val temaBeskrivelse: String?,
    val innhold: String?,
    val dato: LocalDate? = null,
)

fun no.ks.fiks.nhn.msh.Patient.toSerializable() =
    Pasient(this.fnr, this.firstName, this.middleName, this.lastName)

fun no.ks.fiks.nhn.msh.Notat.toSerializable() =
    Notat(this.temaBeskrivelse, this.innhold, this.dato?.toKotlinLocalDate())

fun no.ks.fiks.nhn.msh.Dialogmelding.toSerializable() =
    Dialogmelding(this.foresporsel?.toSerializable(), this.notat?.toSerializable())

fun no.ks.fiks.nhn.msh.Foresporsel.toSerializable() =
    Foresporsel(this.sporsmal)

fun no.ks.fiks.nhn.msh.IncomingBusinessDocument.toSerializable(): IncomingBusinessDocument {

    val metadataFiler = HashMap<String, String>()

    this.vedlegg?.let {
        it.description?.let { description -> metadataFiler.put(AttachmentNames.vedlegg(0), description) }
    }

    return IncomingBusinessDocument(
        this.id,
        this.sender.child.herId!!,
        this.receiver.child.herId!!,
        this.conversationRef?.refToConversation,
        this.conversationRef?.refToParent,
        DialogmeldingMessage(
            AttachmentNames.DIALOGMELDING,
            this.receiver.patient.toSerializable(),
            metadataFiler.toMap()
        )
    )
}
