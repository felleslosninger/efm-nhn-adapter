package no.difi.meldingsutveksling.nhn.adapter

data class NhnConfig(val url: String, val username: String, val password: String)

data class HelseId(val clientId: String, val privateKey: String, val issuer: String, val audience: String)
