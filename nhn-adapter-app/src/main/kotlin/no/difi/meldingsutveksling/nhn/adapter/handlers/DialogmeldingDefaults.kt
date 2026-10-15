package no.difi.meldingsutveksling.nhn.adapter.handlers

import no.kith.xmlstds.CS
import no.kith.xmlstds.URL
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.kith.xmlstds.dialog._2013_01_23.HealthcareProfessional
import no.kith.xmlstds.dialog._2013_01_23.Notat
import no.kith.xmlstds.dialog._2013_01_23.RollerRelatertNotat
import no.kith.xmlstds.felleskomponent1.TeleCom
import no.ks.fiks.hdir.Helsepersonell
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.hdir.TemaForHelsefagligDialog
import no.ks.fiks.nhn.ar.AddressComponent
import no.ks.fiks.nhn.ar.CommunicationParty
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.edi.toCV

/** Fills in information required by the Dialogmelding standard that the sender is not expected to provide. */
object DialogmeldingDefaults {

    fun apply(dialogmelding: Dialogmelding, receiver: CommunicationParty) {
        dialogmelding.notat
            .filter { it.isHenvendelseOmPasient() && it.rollerRelatertNotat.isEmpty() }
            .forEach { it.rollerRelatertNotat = defaultRoller(receiver) }
    }

    private fun Notat.isHenvendelseOmPasient() = temaKodet?.v == TemaForHelsefagligDialog.HENVENDELSE_OM_PASIENT.verdi

    /**
     * When a Helsefaglig dialog is created without referring to an earlier message, the role "Kontakt hos mottaker"
     * must be given with profession group and optionally the name of the healthcare professional. This only applies to
     * code value 6 "Henvendelse om pasient" in the element tema kodet.
     *
     * A person receiver is the patient's fastlege; organizations and services have no fastlege role.
     */
    private fun defaultRoller(receiver: CommunicationParty): List<RollerRelatertNotat> =
        listOfNotNull((receiver as? PersonCommunicationParty)?.let { fastlegeRolle(it) }, kontaktHosMottakerRolle())

    private fun fastlegeRolle(fastlege: PersonCommunicationParty) =
        RollerRelatertNotat().apply {
            roleToPatient = HelsepersonellsFunksjoner.FASTLEGE.toCV()
            healthcareProfessional = toHealthcareProfessional(fastlege)
        }

    private fun kontaktHosMottakerRolle() =
        RollerRelatertNotat().apply {
            roleToPatient = HelsepersonellsFunksjoner.KONTAKT_HOS_MOTTAKER.toCV()
            healthcareProfessional =
                HealthcareProfessional().apply {
                    typeHealthcareProfessional =
                        CS().apply {
                            dn = Helsepersonell.LEGE.navn
                            v = Helsepersonell.LEGE.verdi
                        }
                }
        }

    private fun toHealthcareProfessional(person: PersonCommunicationParty) =
        HealthcareProfessional().apply {
            givenName = person.firstName
            middleName = person.middleName
            familyName = person.lastName
            person.phoneNumber()?.let { teleCom.add(TeleCom().apply { teleAddress = URL().apply { v = "tel:$it" } }) }
        }

    private fun PersonCommunicationParty.phoneNumber(): String? =
        electronicAddresses.find { it.type == AddressComponent.TELEFONNUMMER }?.address
}
