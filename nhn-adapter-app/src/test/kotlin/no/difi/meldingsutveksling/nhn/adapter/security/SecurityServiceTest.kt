package no.difi.meldingsutveksling.nhn.adapter.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.difi.meldingsutveksling.nhn.adapter.ArTestData
import no.difi.meldingsutveksling.nhn.adapter.config.SecurityConfig
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import org.springframework.security.access.AccessDeniedException

private const val HER_ID = 1000002
private const val ORGANIZATION_HER_ID = 1000003
private const val SERVICE_HER_ID = 1000004
private const val OWNER_ORG_NUMBER = "123456789"
private const val SUPPLIER_ORG_NUMBER = "987654321"
private const val TRUSTED_DELEGATION_SOURCE = "altinn"

class SecurityServiceTest :
    DescribeSpec({
        val party =
            ArTestData.person(
                HER_ID,
                "Lege Legesen",
                ArTestData.parentOrganization(1000001, "Test kommune", OWNER_ORG_NUMBER),
            )
        val organization = ArTestData.organization(ORGANIZATION_HER_ID, "Test kommune", OWNER_ORG_NUMBER)
        val serviceWithoutOrgNumber =
            ArTestData.service(
                SERVICE_HER_ID,
                "Tjeneste",
                ArTestData.parentOrganization(1000001, "Test kommune", null),
            )

        val adresseregisteretService = mockk<AdresseregisteretService>()
        coEvery { adresseregisteretService.lookupByHerId(HER_ID) } returns party
        coEvery { adresseregisteretService.lookupByHerId(ORGANIZATION_HER_ID) } returns organization
        coEvery { adresseregisteretService.lookupByHerId(SERVICE_HER_ID) } returns serviceWithoutOrgNumber

        val securityService =
            SecurityService(SecurityConfig(emptyList(), TRUSTED_DELEGATION_SOURCE), adresseregisteretService)

        fun clientContext(orgNumber: String, onBehalfOf: String, delegationSource: String? = null) =
            mockk<ClientContext>().also {
                every { it.orgNumber } returns orgNumber
                every { it.onBehalfOfOrgNumber } returns onBehalfOf
                every { it.delegationSource } returns delegationSource
            }

        describe("assertAccessToHerId") {
            it("grants access to the organization owning the HER-id") {
                securityService.assertAccessToHerId(clientContext(OWNER_ORG_NUMBER, OWNER_ORG_NUMBER), HER_ID) shouldBe
                    party
            }

            it("grants access to a supplier delegated through the trusted delegation source") {
                securityService.assertAccessToHerId(
                    clientContext(SUPPLIER_ORG_NUMBER, OWNER_ORG_NUMBER, TRUSTED_DELEGATION_SOURCE),
                    HER_ID,
                ) shouldBe party
            }

            it("denies a supplier delegated through another delegation source") {
                shouldThrow<AccessDeniedException> {
                    securityService.assertAccessToHerId(
                        clientContext(SUPPLIER_ORG_NUMBER, OWNER_ORG_NUMBER, "other"),
                        HER_ID,
                    )
                }
            }

            it("grants access to an organization using its own organization number") {
                securityService.assertAccessToHerId(
                    clientContext(OWNER_ORG_NUMBER, OWNER_ORG_NUMBER),
                    ORGANIZATION_HER_ID,
                ) shouldBe organization
            }

            it("denies another organization access to an organization") {
                shouldThrow<AccessDeniedException> {
                    securityService.assertAccessToHerId(
                        clientContext(SUPPLIER_ORG_NUMBER, SUPPLIER_ORG_NUMBER),
                        ORGANIZATION_HER_ID,
                    )
                }
            }

            it("fails when the owning organization has no organization number") {
                shouldThrow<IllegalStateException> {
                    securityService.assertAccessToHerId(
                        clientContext(OWNER_ORG_NUMBER, OWNER_ORG_NUMBER),
                        SERVICE_HER_ID,
                    )
                }
            }

            it("denies another organization") {
                shouldThrow<AccessDeniedException> {
                    securityService.assertAccessToHerId(
                        clientContext(SUPPLIER_ORG_NUMBER, SUPPLIER_ORG_NUMBER),
                        HER_ID,
                    )
                }
            }
        }
    })
