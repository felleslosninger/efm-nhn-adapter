package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import javax.xml.stream.XMLStreamException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

class XMLUtilsTest :
    DescribeSpec({
        describe("getRootElement") {
            it("returns the local name of the root element") {
                XMLUtils.getRootElement("""<ns:AppRec xmlns:ns="urn:test"><Id>1</Id></ns:AppRec>""") shouldBe "AppRec"
            }

            it("skips the prolog") {
                XMLUtils.getRootElement(
                    """<?xml version="1.0" encoding="UTF-8"?><!-- a comment --><?pi data?><MsgHead/>"""
                ) shouldBe "MsgHead"
            }

            it("gives up on a prolog that is too long") {
                val prolog = (1..150).joinToString("") { "<!-- $it -->" }

                XMLUtils.getRootElement("$prolog<MsgHead/>").shouldBeNull()
            }

            it("rejects an empty document as not well-formed") {
                shouldThrow<XMLStreamException> { XMLUtils.getRootElement("") }
            }

            // A DOCTYPE only declares entities, which can read external resources (XXE)
            it("rejects a DOCTYPE") {
                shouldThrow<ApplicationReceiptException> {
                        XMLUtils.getRootElement("""<!DOCTYPE MsgHead [<!ENTITY x "y">]><MsgHead>&x;</MsgHead>""")
                    }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.UGYLDIG_XML
            }
        }

        describe("validateRootElement") {
            it("accepts the expected root element") {
                shouldNotThrowAny { XMLUtils.validateRootElement("<AppRec/>", "AppRec") }
            }

            it("rejects another root element") {
                shouldThrow<ApplicationReceiptException> { XMLUtils.validateRootElement("<MsgHead/>", "AppRec") }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT
            }
        }

        describe("getVersion") {
            it("returns the MIGversion") {
                XMLUtils.getVersion("<AppRec><MIGversion>v1.1 2012-02-15</MIGversion></AppRec>") shouldBe
                    "v1.1 2012-02-15"
            }

            it("returns null when there is no MIGversion") {
                XMLUtils.getVersion("<AppRec><Id>1</Id></AppRec>").shouldBeNull()
            }

            it("returns null for an empty MIGversion") {
                XMLUtils.getVersion("<AppRec><MIGversion/></AppRec>").shouldBeNull()
            }

            it("reads all the text of the MIGversion, trimmed") {
                XMLUtils.getVersion("<AppRec><MIGversion>\n  v1.1 2012-02-15\n</MIGversion></AppRec>") shouldBe
                    "v1.1 2012-02-15"
                XMLUtils.getVersion("<AppRec><MIGversion><![CDATA[v1.1 2012-02-15]]></MIGversion></AppRec>") shouldBe
                    "v1.1 2012-02-15"
                XMLUtils.getVersion("<AppRec><MIGversion><!-- c -->v1.1 2012-02-15</MIGversion></AppRec>") shouldBe
                    "v1.1 2012-02-15"
            }

            it("reports an error in the text of the MIGversion as XML that is not well-formed") {
                shouldThrow<XMLStreamException> {
                    XMLUtils.getVersion("<AppRec><MIGversion>v1.1 &bogus; 2012-02-15</MIGversion></AppRec>")
                }
            }

            it("rejects a DOCTYPE") {
                shouldThrow<ApplicationReceiptException> {
                        XMLUtils.getVersion(
                            """<!DOCTYPE AppRec><AppRec><MIGversion>v1.1 2012-02-15</MIGversion></AppRec>"""
                        )
                    }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.UGYLDIG_XML
            }
        }
    })
