package no.difi.meldingsutveksling.nhn.adapter.audit

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain

private const val KEY = "test-pseudonymization-key-of-at-least-32-characters"
private const val FNR = "16822449879"

class IdentifierPseudonymizerTest :
    DescribeSpec({
        val pseudonymizer = IdentifierPseudonymizer(KEY)

        describe("pseudonymize") {
            it("keeps a HER-id") { pseudonymizer.pseudonymize("her-id:2000002") shouldBe "her-id:2000002" }

            it("replaces the fødselsnummer with its HMAC, keeping the type") {
                // printf %s $FNR | openssl dgst -sha256 -hmac $KEY
                pseudonymizer.pseudonymize("fastlege-for:$FNR") shouldBe
                    "fastlege-for:hmac:aca73a5194a48634e0a796fdb2ea254ad08524775bf119b72557c4e695a1eb47"
            }

            it("gives the same pseudonym for the same fødselsnummer") {
                pseudonymizer.pseudonymize("fastlege-for:$FNR") shouldBe
                    pseudonymizer.pseudonymize("fastlege-for:$FNR")
            }

            it("gives another pseudonym with another key") {
                IdentifierPseudonymizer(KEY.reversed()).pseudonymize("fastlege-for:$FNR") shouldNotBe
                    pseudonymizer.pseudonymize("fastlege-for:$FNR")
            }

            it("hashes only the fødselsnummer when its check digit is wrong") {
                // printf %s 16822449870 | openssl dgst -sha256 -hmac $KEY
                pseudonymizer.pseudonymize("fastlege-for:16822449870") shouldBe
                    "fastlege-for:hmac:5fa5fb5fc8d8a38466fd74af952c84fcd8f1b6b2b1f955143837114b62d6c8c9"
            }

            listOf("her-id:$FNR", FNR, "not-an-identifier:$FNR", "fastlege-for:1682244987").forEach { identifier ->
                it("hashes the invalid identifier '$identifier'") {
                    val pseudonym = pseudonymizer.pseudonymize(identifier)

                    pseudonym shouldNotContain FNR
                    pseudonym shouldNotContain identifier.substringAfter(':')
                }
            }
        }

        it("rejects a short key") { shouldThrow<IllegalArgumentException> { IdentifierPseudonymizer("too short") } }
    })
