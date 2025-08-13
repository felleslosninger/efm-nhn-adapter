package no.difi.meldingsutveksling.nhn.adapter

data class Configuration(val nhn: Services, val helseId: HelseId)

data class Services(val services: NhnConfig)

data class NhnConfig(val username: String, val password: String)

data class HelseId(val clientId: String, val privateKey: String, val issuer: String, val audience: String)
