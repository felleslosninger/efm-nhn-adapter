package no.difi.meldingsutveksling.nhn.adapter.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.MeldingensFunksjon
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.msh.ChildOrganization
import no.ks.fiks.nhn.msh.Department
import no.ks.fiks.nhn.msh.Dialogmelding
import no.ks.fiks.nhn.msh.Foresporsel
import no.ks.fiks.nhn.msh.Id
import no.ks.fiks.nhn.msh.IncomingApplicationReceipt
import no.ks.fiks.nhn.msh.IncomingApplicationReceiptError
import no.ks.fiks.nhn.msh.IncomingBusinessDocument
import no.ks.fiks.nhn.msh.IncomingVedlegg
import no.ks.fiks.nhn.msh.Institution
import no.ks.fiks.nhn.msh.InstitutionPerson
import no.ks.fiks.nhn.msh.Notat
import no.ks.fiks.nhn.msh.Organization
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.OrganizationReceiverDetails
import no.ks.fiks.nhn.msh.OutgoingApplicationReceipt
import no.ks.fiks.nhn.msh.OutgoingApplicationReceiptError
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.PersonId
import no.ks.fiks.nhn.msh.PersonReceiverDetails
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.ReceiverDetails

@Serializable
data class SerializableOutgoingApplicationReceipt
@OptIn(ExperimentalUuidApi::class)
constructor(
    val acknowledgedId: Uuid,
    val senderHerId: Int,
    @Serializable(with = StatusForMottakAvMeldingSerializer::class) val status: StatusForMottakAvMelding,
    val errors: List<SerializeableOutgoingApplicationReceiptError>? = null,
    val recieverHerId: Int? = null,
)

@OptIn(ExperimentalUuidApi::class)
fun OutgoingApplicationReceipt.toSerializable(): SerializableOutgoingApplicationReceipt =
    SerializableOutgoingApplicationReceipt(
        acknowledgedId = this.acknowledgedId.toKotlinUuid(),
        senderHerId = this.senderHerId,
        status = this.status,
        errors = this.errors?.map { it.toSerializable() },
    )

@OptIn(ExperimentalUuidApi::class)
fun SerializableOutgoingApplicationReceipt.toOriginal(): OutgoingApplicationReceipt =
    OutgoingApplicationReceipt(
        acknowledgedId = this.acknowledgedId.toJavaUuid(),
        senderHerId = this.senderHerId,
        status = this.status,
        errors = this.errors?.map { it.toOriginal() },
    )

@Serializable
data class SerializeableOutgoingApplicationReceiptError(
    @Serializable(with = FeilmeldingForApplikasjonskvitteringSerializer::class)
    val type: FeilmeldingForApplikasjonskvittering,
    val details: String? = null,
)

fun SerializeableOutgoingApplicationReceiptError.toOriginal(): OutgoingApplicationReceiptError =
    OutgoingApplicationReceiptError(type = this.type, details = this.details)

fun OutgoingApplicationReceiptError.toSerializable() =
    SerializeableOutgoingApplicationReceiptError(this.type, this.details)

fun IncomingApplicationReceiptError.toSerializable(): SerializableIncomingApplicationReceiptError =
    SerializableIncomingApplicationReceiptError(
        type = this.type,
        details = this.details,
        errorCode = this.errorCode,
        description = this.description,
        oid = this.oid,
    )

fun SerializableIncomingApplicationReceiptError.toOriginal(): IncomingApplicationReceiptError =
    IncomingApplicationReceiptError(
        type = this.type,
        details = this.details,
        this.errorCode,
        this.description,
        this.oid,
    )

@Serializable
data class SerializableIncomingApplicationReceipt(
    val id: String,
    val acknowledgedBusinessDocumentId: String, // Adjust to UUID if needed
    @Serializable(with = StatusForMottakAvMeldingSerializer::class) val status: StatusForMottakAvMelding,
    val errors: List<SerializableIncomingApplicationReceiptError>,
    val sender: SerializableInstitution,
    val receiver: SerializableInstitution,
)

@Serializable
data class SerializableInstitution(
    val name: String,
    @Serializable(with = IdSerializer::class) val id: Id, // Adjust if Id is UUID or another type
    val department: SerializableDepartment? = null,
    val person: SerializableInstitutionPerson? = null,
)

@Serializable
data class SerializableDepartment(val name: String, @Serializable(with = IdSerializer::class) val id: Id)

@Serializable
data class SerializableInstitutionPerson(val name: String, @Serializable(with = IdSerializer::class) val id: Id)

@Serializable
data class SerializeableIncomingBusinessDocument(
    val id: String,
    val date: LocalDateTime?,
    val type: MeldingensFunksjon,
    val sender: SerializeableOrganization,
    val receiver: SerializeableReceiver,
    val message: SerializeableDialogmelding?,
    val vedlegg: SerializeableIncomingVedlegg?,
)

@Serializable
data class SerializeableOrganization(
    val name: String,
    @Serializable(with = IdListSerializer::class) val ids: List<Id>,
    val childOrganization: SerializeableChildOrganization?,
)

@Serializable
data class SerializableOrganization(
    val name: String,
    val ids: List<String>,
    val childOrganization: SerializeableChildOrganization?,
)

@Serializable
data class SerializeableChildOrganization(
    val name: String,
    @Serializable(with = IdListSerializer::class) val ids: List<Id>,
)

@Serializable
data class SerializeableReceiver(
    val parent: SerializeableReceiverDetails,
    val child: SerializeableReceiverDetails,
    val patient: SerializeablePatient,
)

@Serializable
sealed interface SerializeableReceiverDetails {
    @Serializable(with = IdListSerializer::class) val ids: List<Id>
}

@Serializable
@SerialName("organization")
data class SerializeableOrganizationReceiverDetails(
    @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
    val name: String,
) : SerializeableReceiverDetails

@Serializable
@SerialName("person")
data class SerializeablePersonReceiverDetails(
    @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
) : SerializeableReceiverDetails

@Serializable
data class SerializeablePatient(val fnr: String, val firstName: String, val middleName: String?, val lastName: String)

@Serializable
data class SerializeableDialogmelding(@Transient val foresporsel: Foresporsel? = null, val notat: SerializeableNotat?)

@Serializable
data class SerializeableNotat(
    val tema: String,
    val temaBeskrivelse: String?,
    val innhold: String?,
    val dato: LocalDate?,
)

@Serializable
data class SerializeableIncomingVedlegg
@OptIn(ExperimentalTime::class)
constructor(@Contextual val date: Instant?, val description: String?, val mimeType: String?, val data: ByteArray?)

fun ChildOrganization.toSerializeable() = SerializeableChildOrganization(name = name, ids = ids)

fun Organization.toSerializable() =
    SerializeableOrganization(name = name, ids = ids, childOrganization = childOrganization?.toSerializeable())

fun ReceiverDetails.toSerializeable() =
    when (this) {
        is OrganizationReceiverDetails -> SerializeableOrganizationReceiverDetails(this.ids, this.name)
        is PersonReceiverDetails ->
            SerializeablePersonReceiverDetails(this.ids, this.firstName, this.middleName, this.lastName)
    }

fun Patient.toSerializeable() = SerializeablePatient(this.fnr, this.firstName, this.middleName, this.lastName)

fun Receiver.toSerializable() =
    SerializeableReceiver(this.parent.toSerializeable(), this.child.toSerializeable(), this.patient.toSerializeable())

/*
data class IncomingBusinessDocument(
    val id: String,
    val date: LocalDateTime?,
    val type: MeldingensFunksjon,
    val sender: Organization,
    val receiver: Receiver,
    val message: Dialogmelding?,
    val vedlegg: IncomingVedlegg?,
)*/

fun IncomingApplicationReceipt.toSerializable(): SerializableIncomingApplicationReceipt =
    SerializableIncomingApplicationReceipt(
        id = this.id,
        acknowledgedBusinessDocumentId = this.acknowledgedBusinessDocumentId,
        status = this.status,
        errors = this.errors.map { it.toSerializable() },
        sender = this.sender.toSerializable(),
        receiver = this.receiver.toSerializable(),
    )

fun SerializableIncomingApplicationReceipt.toOriginal(): IncomingApplicationReceipt =
    IncomingApplicationReceipt(
        id = this.id,
        acknowledgedBusinessDocumentId = this.acknowledgedBusinessDocumentId,
        status = this.status,
        errors = this.errors.map { it.toOriginal() },
        sender = this.sender.toOriginal(),
        receiver = this.receiver.toOriginal(),
    )

fun Institution.toSerializable(): SerializableInstitution =
    SerializableInstitution(
        name = this.name,
        id = this.id,
        department = this.department?.toSerializable(),
        person = this.person?.toSerializable(),
    )

fun SerializableInstitution.toOriginal(): Institution =
    Institution(
        name = this.name,
        id = this.id,
        department = this.department?.toOriginal(),
        person = this.person?.toOriginal(),
    )

fun Notat.toSerializable() =
    SerializeableNotat(this.tema.verdi, this.temaBeskrivelse, this.innhold, this.dato?.toKotlinLocalDate())

fun Dialogmelding.toSerializeable() = SerializeableDialogmelding(this.foresporsel, this.notat?.toSerializable())

@OptIn(ExperimentalTime::class)
fun IncomingVedlegg.toSerializeable() =
    SerializeableIncomingVedlegg(
        this.date?.toInstant()?.toKotlinInstant(),
        this.description,
        this.mimeType,
        this.data?.readAllBytes(),
    )

fun IncomingBusinessDocument.toSerializeable() =
    SerializeableIncomingBusinessDocument(
        this.id,
        this.date?.toKotlinLocalDateTime(),
        this.type,
        this.sender.toSerializable(),
        this.receiver.toSerializable(),
        this.message?.toSerializeable(),
        this.vedlegg?.toSerializeable(),
    )

fun Department.toSerializable(): SerializableDepartment = SerializableDepartment(name = this.name, id = this.id)

fun SerializableDepartment.toOriginal(): Department = Department(name = this.name, id = this.id)

fun InstitutionPerson.toSerializable(): SerializableInstitutionPerson =
    SerializableInstitutionPerson(name = this.name, id = this.id)

fun SerializableInstitutionPerson.toOriginal(): InstitutionPerson = InstitutionPerson(name = this.name, id = this.id)

object IdSerializer : KSerializer<Id> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Id") {
            element("id", PrimitiveSerialDescriptor("id", PrimitiveKind.STRING))
            element("type", PrimitiveSerialDescriptor("type", PrimitiveKind.STRING))
        }

    override fun serialize(encoder: Encoder, value: Id) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeStringElement(
                descriptor,
                1,
                when (value) {
                    is PersonId -> value.type.toString() // Adjust based on PersonIdType
                    is OrganizationId -> value.type.toString() // Adjust based on OrganizationIdType
                    else -> throw IllegalArgumentException("Unknown Id subtype: ${value::class}")
                },
            )
        }
    }

    override fun deserialize(decoder: Decoder): Id =
        decoder.decodeStructure(descriptor) {
            var id: String? = null
            var type: String? = null
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    -1 -> break
                    0 -> id = decodeStringElement(descriptor, 0)
                    1 -> type = decodeStringElement(descriptor, 1)
                    else -> throw IllegalArgumentException("Unexpected index: $index")
                }
            }
            if (id == null || type == null) {
                throw IllegalArgumentException("Missing id or type")
            }
            // Adjust based on actual PersonIdType and OrganizationIdType values
            when (type) {
                // Example: Replace with actual PersonIdType/OrganizationIdType string values
                "PERSON_ID" -> PersonId(id, PersonIdType.valueOf(type)) // Adjust enum
                "ORGANIZATION_ID" -> OrganizationId(id, OrganizationIdType.valueOf(type)) // Adjust enum
                else -> throw IllegalArgumentException("Unknown Id type: $type")
            }
        }
}

object IdListSerializer : KSerializer<List<Id>> {
    private val delegate = ListSerializer(IdSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<Id>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<Id> = delegate.deserialize(decoder)
}
