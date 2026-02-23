package no.difi.meldingsutveksling.nhn.adapter.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.Serializable
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.msh.Department
import no.ks.fiks.nhn.msh.Id
import no.ks.fiks.nhn.msh.IncomingApplicationReceipt
import no.ks.fiks.nhn.msh.IncomingApplicationReceiptError
import no.ks.fiks.nhn.msh.Institution
import no.ks.fiks.nhn.msh.InstitutionPerson
import no.ks.fiks.nhn.msh.OutgoingApplicationReceipt
import no.ks.fiks.nhn.msh.OutgoingApplicationReceiptError

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

fun Department.toSerializable(): SerializableDepartment = SerializableDepartment(name = this.name, id = this.id)

fun SerializableDepartment.toOriginal(): Department = Department(name = this.name, id = this.id)

fun InstitutionPerson.toSerializable(): SerializableInstitutionPerson =
    SerializableInstitutionPerson(name = this.name, id = this.id)

fun SerializableInstitutionPerson.toOriginal(): InstitutionPerson = InstitutionPerson(name = this.name, id = this.id)
