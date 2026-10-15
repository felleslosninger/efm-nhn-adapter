package no.difi.meldingsutveksling.nhn.adapter.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AttachmentNamesTest :
    DescribeSpec({
        describe("AttachmentNames.vedlegg") {
            it("uses the file suffix of known MIME types") {
                AttachmentNames.vedlegg(0, "application/pdf") shouldBe "vedlegg0.pdf"
                AttachmentNames.vedlegg(1, "image/jpeg") shouldBe "vedlegg1.jpg"
                AttachmentNames.vedlegg(2, "image/jpg") shouldBe "vedlegg2.jpg"
                AttachmentNames.vedlegg(3, "image/png") shouldBe "vedlegg3.png"
                AttachmentNames.vedlegg(4, "text/plain") shouldBe "vedlegg4.txt"
            }

            it("falls back to a binary suffix for other MIME types") {
                AttachmentNames.vedlegg(5, "application/zip") shouldBe "vedlegg5.bin"
            }
        }
    })
