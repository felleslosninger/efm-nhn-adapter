package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getReceiver
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.DialogmeldingDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.XMLUtils
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.Sender

/** Placed in an external file and served over HTTP. It must never show up in a parsed result or an error message. */
private const val SECRET = "XXE-SECRET-6f1d0c"

/** Where a parser's input contains `&xxe;`, so that an expanded entity would show up in the parsed value. */
private const val ENTITY = "&xxe;"

/**
 * A parser of untrusted XML, the document it accepts with [ENTITY] placed in a text value, and how to read that value
 * back from the result.
 */
private class XmlParser(val root: String, val document: String, val parse: (String) -> String?)

/**
 * Verifies that no XML parser fed with untrusted input (business documents and receipts from MSH, and dialogmeldinger
 * from clients) reads external resources: no external entities, external DTDs or parameter entities, over file: or
 * http:. A served file or HTTP resource would otherwise leak into the parsed message or into the error message
 * returned to the client, or let the adapter be used to make requests (SSRF).
 */
class XmlExternalEntityTest :
    DescribeSpec({
        val secretFile = Files.createTempFile("xxe-", ".txt").apply { writeText(SECRET) }
        val httpRequests = AtomicInteger()
        val server =
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
                createContext("/") { exchange ->
                    httpRequests.incrementAndGet()
                    // A DTD defining the entity for the external DTD and parameter entity cases,
                    // the secret otherwise
                    val body = if (exchange.requestURI.path.endsWith(".dtd")) """<!ENTITY xxe "$SECRET">""" else SECRET
                    val bytes = body.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                start()
            }
        val baseUrl = "http://${server.address.hostString}:${server.address.port}"

        afterSpec {
            server.stop(0)
            secretFile.deleteIfExists()
        }

        beforeTest { httpRequests.set(0) }

        val attacks =
            mapOf(
                "external entity over file:" to
                    { root: String ->
                        """<!DOCTYPE $root [<!ENTITY xxe SYSTEM "${secretFile.toUri()}">]>"""
                    },
                "external entity over http:" to
                    { root: String ->
                        """<!DOCTYPE $root [<!ENTITY xxe SYSTEM "$baseUrl/secret">]>"""
                    },
                "external DTD" to { root: String -> """<!DOCTYPE $root SYSTEM "$baseUrl/external.dtd">""" },
                "external parameter entity" to
                    { root: String ->
                        """<!DOCTYPE $root [<!ENTITY % ext SYSTEM "$baseUrl/parameter.dtd"> %ext;]>"""
                    },
            )

        val parsers =
            mapOf(
                "MsgHead from MSH" to
                    XmlParser("MsgHead", msgHead()) {
                        BusinessDocumentDeserializer.deserializeMsgHead(it).getReceiver().patient.firstName
                    },
                "AppRec 1.1 from MSH" to
                    XmlParser("AppRec", appRec("http://www.kith.no/xmlstds/apprec/2012-02-15", "v1.1 2012-02-15")) {
                        ApplicationReceiptDeserializer.deserializeAppRec(it).softwareName
                    },
                "AppRec 1.0 from MSH" to
                    XmlParser("AppRec", appRec("http://www.kith.no/xmlstds/apprec/2004-11-21", "1.0 2004-11-21")) {
                        ApplicationReceiptDeserializer.deserializeAppRec(it).softwareName
                    },
                "Dialogmelding from a client" to
                    XmlParser("Dialogmelding", DIALOGMELDING) {
                        DialogmeldingDeserializer.deserializeDialogmelding(it).notat.single().tema
                    },
                "MIGversion lookup" to
                    XmlParser("AppRec", """<AppRec><MIGversion>$ENTITY</MIGversion></AppRec>""") {
                        XMLUtils.getVersion(it)
                    },
            )

        parsers.forEach { (parserName, parser) ->
            describe(parserName) {
                withData(attacks) { doctype ->
                    val outcome = runCatching { parser.parse(withDoctype(parser.document, doctype(parser.root))) }

                    outcome.getOrNull().orEmpty() shouldNotContain SECRET
                    outcome.exceptionOrNull()?.causes()?.forEach { it.message.orEmpty() shouldNotContain SECRET }
                    httpRequests.get() shouldBe 0
                }

                it("does not expand nested entities (billion laughs)") {
                    val outcome = runCatching {
                        parser.parse(
                            withDoctype(parser.document, billionLaughs(parser.root)).replace(ENTITY, "&lol9;")
                        )
                    }

                    outcome.getOrNull().orEmpty().length shouldBeLessThan 10_000
                }
            }
        }
    })

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }

/** Puts [doctype] after the XML declaration, if there is one. */
private fun withDoctype(document: String, doctype: String): String {
    val prologEnd = if (document.startsWith("<?xml")) document.indexOf("?>") + 2 else 0
    return document.substring(0, prologEnd) + doctype + document.substring(prologEnd)
}

/** Ten levels of ten entities each, which expands to 10^9 copies of "lol". */
private fun billionLaughs(root: String): String {
    val entities = (1..9).joinToString("") { level -> """<!ENTITY lol$level "${"&lol${level - 1};".repeat(10)}">""" }
    return """<!DOCTYPE $root [<!ENTITY lol0 "lol">$entities]>"""
}

private val DIALOGMELDING =
    """
    <Dialogmelding xmlns="http://www.kith.no/xmlstds/dialog/2013-01-23">
      <Notat>
        <TemaKodet V="6" S="2.16.578.1.12.4.1.1.7322" DN="Henvendelse om pasient"/>
        <Tema>$ENTITY</Tema>
      </Notat>
    </Dialogmelding>
    """
        .trimIndent()

private fun appRec(namespace: String, migVersion: String) =
    """
    <AppRec xmlns="$namespace">
      <MsgType V="APPREC" DN="Applikasjonskvittering"/>
      <MIGversion>$migVersion</MIGversion>
      <SoftwareName>$ENTITY</SoftwareName>
      <SoftwareVersion>1.0</SoftwareVersion>
      <GenDate>2026-10-14T12:00:00</GenDate>
      <Id>apprec-1</Id>
      <Sender><Role V="1" DN="Avsender"/><HCP><Inst><Name>Legekontor</Name><Id>2000001</Id><TypeId V="HER" DN="HER-id"/></Inst></HCP></Sender>
      <Receiver><HCP><Inst><Name>Kommune</Name><Id>1000001</Id><TypeId V="HER" DN="HER-id"/></Inst></HCP></Receiver>
      <Status V="1" DN="OK"/>
      <OriginalMsgId>
        <MsgType V="DIALOG_NOTAT" DN="Notat"/>
        <IssueDate>2026-10-14T11:00:00</IssueDate>
        <Id>original-1</Id>
      </OriginalMsgId>
    </AppRec>
    """
        .trimIndent()

/** A valid MsgHead, with [ENTITY] as the patient's first name. */
private fun msgHead(): String {
    fun organization(herId: Int, name: String) =
        OrganizationCommunicationParty(
            ids = listOf(OrganizationId(herId.toString(), OrganizationIdType.HER_ID)),
            address = null,
            name = name,
        )

    val patientFirstName = "Kari-${UUID.randomUUID()}"
    val xml =
        BusinessDocumentSerializer.serializeNhnMessage(
            SendMessageInput(
                id = UUID.randomUUID(),
                sender = Sender(organization(2000001, "Legekontoret"), organization(2000003, "Resepsjonen")),
                receiver =
                    Receiver(
                        organization(1000001, "Test kommune"),
                        organization(1000002, "Helse og omsorg"),
                        Patient("16822449879", patientFirstName, null, "Nordmann"),
                    ),
                dialogmelding = DialogmeldingDeserializer.deserializeDialogmelding(DIALOGMELDING_WITHOUT_ENTITY),
                vedlegg = emptyList(),
                metadataFiler = null,
                conversationRef = null,
            )
        )
    check(xml.contains(patientFirstName)) { "Expected the patient's first name in the MsgHead" }
    return xml.replace(patientFirstName, ENTITY)
}

private val DIALOGMELDING_WITHOUT_ENTITY = DIALOGMELDING.replace(ENTITY, "Test tema")
