package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import no.kith.xmlstds.dialog._2006_10_11.Dialogmelding as Dialogmelding1_0
import no.kith.xmlstds.dialog._2006_10_11.Foresporsel as Foresporsel1_0
import no.kith.xmlstds.dialog._2006_10_11.Person as Person1_0
import no.kith.xmlstds.dialog._2006_10_11.RollerRelatertNotat as RollerRelatertNotat1_0
import no.kith.xmlstds.dialog._2006_10_11.HealthcareProfessional as HealthcareProfessional1_0
import no.kith.xmlstds.dialog._2006_10_11.Notat as Notat1_0
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.kith.xmlstds.dialog._2013_01_23.Foresporsel
import no.kith.xmlstds.dialog._2013_01_23.Person
import no.kith.xmlstds.dialog._2013_01_23.RollerRelatertNotat
import no.kith.xmlstds.dialog._2013_01_23.HealthcareProfessional
import no.kith.xmlstds.dialog._2013_01_23.Notat

object DialogmeldingConverter {
    fun toLatest(input: Dialogmelding1_0): Dialogmelding {
        return Dialogmelding().apply {
            sakstypeKodet = input.sakstypeKodet
            sakstype = input.sakstype
            foresporsel = input.foresporsel?.map { it.toLatest() }
            notat = input.notat?.map { it.toLatest() }
        }
    }

    private fun Foresporsel1_0.toLatest(): Foresporsel {
        val input = this
        return Foresporsel().apply {
            typeForesp = input.typeForesp
            sporsmal = input.sporsmal
            formål = input.formål
            begrunnelse = input.begrunnelse
            hastegrad = input.hastegrad
            fraDato = input.fraDato
            tilDato = input.tilDato
            typeJournalinfo = input.typeJournalinfo?.let { listOf(it) }
            kodetOpplysning = input.typeForesp
            dokIdForesp = input.dokIdForesp
            rollerRelatertNotat = input.rollerRelatertNotat?.map { it.toLatest() }
        }
    }

    private fun RollerRelatertNotat1_0.toLatest(): RollerRelatertNotat {
        val input = this
        return RollerRelatertNotat().apply {
            rolleNotat = input.rolleNotat
            roleToPatient = input.roleToPatient
            healthcareProfessional = input.healthcareProfessional?.toLatest()
            person = input.person?.toLatest()
        }
    }

    private fun HealthcareProfessional1_0.toLatest(): HealthcareProfessional {
        val input = this
        return HealthcareProfessional().apply {
            typeHealthcareProfessional = input.typeHealthcareProfessional
            roleToPatient = input.roleToPatient
            familyName = input.familyName
            middleName = input.middleName
            givenName = input.givenName
            dateOfBirth = input.dateOfBirth
            sex = input.sex
            nationality = input.nationality
            ident = input.ident
            address = input.address
            teleCom = input.teleCom
        }
    }

    private fun Person1_0.toLatest(): Person {
        val input = this
        return Person().apply {
            familyName = input.familyName
            middleName = input.middleName
            givenName = input.givenName
            dateOfBirth = input.dateOfBirth
            sex = input.sex
            nationality = input.nationality
            ident = input.ident
            address = input.address
            teleCom = input.teleCom
        }
    }

    private fun Notat1_0.toLatest(): Notat {
        val input = this
        return Notat().apply {
            temaKodet = input.temaKodet
            tema = input.tema
            tekstNotatInnhold = input.tekstNotatInnhold
            merknad = input.merknad
            dokIdNotat = input.dokIdNotat
            datoNotat = input.datoNotat
            foresporsel = input.foresporsel?.toLatest()
            rollerRelatertNotat = input.rollerRelatertNotat?.map { it.toLatest() }
        }
    }
}

