package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
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

                shouldThrow<IllegalStateException> { XMLUtils.getRootElement("$prolog<MsgHead/>") }
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
        }
    })
