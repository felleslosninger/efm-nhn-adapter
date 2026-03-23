# efm-nhn-adapter


# Dette er ein test

efm-nhn-adapter er eit adapter for sending og mottak av meldingar via Norsk Helsenett si meldingsteneste (DPH) frå [eFormidling](https://github.com/digdir/efm-integrasjonspunkt).

Adapteret skal handtere blant anna:

- Autentisering via HelseID
- Adressering til fastlege basert på fødselsnummer
- Adressering til helseaktør via HER-id
- Sending av meldingar via NHN sitt REST-API
- Handsaming av kvitteringar og eventuelle svarmeldingar

Det er planlagt at adapteret skal byggje på eit bibliotek utvikla av KS Digital. Dette biblioteket er førebels ikkje tilgjengeleg, så det vert nytta mockar/stubbar der det er nødvendig.

## Teknologi

- Java 21
- Maven
- JUnit 5

## Caser
 
### Case 1 - Sending til fastlege med FNR

```mermaid
sequenceDiagram
    participant Fagsystem
    participant Integrasjonspunktet
    participant "Service Registry"
    participant "NHN Adapter"
    participant Adresseregisteret
    participant Fastlegeregisteret
    participant EDI2.0
    Fagsystem ->> Integrasjonspunktet: SBH + vedlegg 
    Integrasjonspunktet ->> "Service Registry": DPH + FNR
    "Service Registry" ->> "NHN Adapter": FNr
    "NHN Adapter" ->> Fastlegeregisteret: FNr
    Fastlegeregisteret ->> "NHN Adapter" : HerId1
    "NHN Adapter" ->> Adresseregisteret: HerId1
    Adresseregisteret ->> "NHN Adapter": HerId2 
    "NHN Adapter" ->> "Service Registry": HerId1 + HerId2 
    "Service Registry" ->> Integrasjonspunktet: Digdir sertifikat + HerId1 + HerId2
    Integrasjonspunktet --) "NHN Adapter": Meldingsinfo med HerId + vedlegg
    "NHN Adapter" ->> EDI2.0: Meldingsinfo med HerId + vedlegg
    EDI2.0 ->> "NHN Adapter": MessageReference
    "NHN Adapter" ->> Integrasjonspunktet: MessageReference
    Integrasjonspunktet ->> Fagsystem: SBH
```

### Case 2 - Sending til fastlege med HerId

```mermaid
sequenceDiagram
    participant Fagsystem
    participant Integrasjonspunktet
    participant "Service Registry"
    participant "NHN Adapter"
    participant Adresseregisteret
    participant EDI2.0
    Fagsystem ->> Integrasjonspunktet: SBH + vedlegg 
    Integrasjonspunktet ->> "Service Registry": DPH + HerId
    "Service Registry" ->> "NHN Adapter": HerId
    "NHN Adapter" ->> Adresseregisteret: HerId
    Adresseregisteret ->> "NHN Adapter": HerId
    "NHN Adapter" ->> "Service Registry": HerId
    "Service Registry" ->> Integrasjonspunktet: Digdir sertifikat + HerId
    Integrasjonspunktet --) "NHN Adapter": Meldingsinfo med HerId + vedlegg
    "NHN Adapter" ->> EDI2.0: Meldingsinfo med HerId + vedlegg
    EDI2.0 ->> "NHN Adapter": MessageReference
    "NHN Adapter" ->> Integrasjonspunktet: MessageReference
    Integrasjonspunktet ->> Fagsystem: SBH
```


### Case 3 - Sending fra fastlege

```mermaid
sequenceDiagram
    participant Fagsystem
    participant Integrasjonspunktet
    participant "Service Registry"
    participant "NHN Adapter"
    participant Adresseregisteret
    participant EDI2.0
    Fagsystem ->> Integrasjonspunktet: HerId2 
    Integrasjonspunktet ->> "Service Registry": DPH + HerId2
    "Service Registry" ->> "NHN Adapter": HerId2
    "NHN Adapter" ->> Adresseregisteret: HerId2
    Adresseregisteret ->> "NHN Adapter": HerId1 + HerId2 + Orgnr
    "NHN Adapter" ->> "Service Registry": HerId1 + HerId2 + Orgnr
    "Service Registry" ->> Integrasjonspunktet: Digdir sertifikat + HerId1 + HerId2 + Orgnr
    Integrasjonspunktet ->> "NHN Adapter": Digdir sertifikat + HerId1 + HerId2 + Orgnr
    "NHN Adapter" ->> EDI2.0: Meldingsinfo med HerId + vedlegg
    EDI2.0 ->> "NHN Adapter": MessageReference
    "NHN Adapter" ->> Integrasjonspunktet: MessageReference
    Integrasjonspunktet ->> Fagsystem: SBH + ASiCe 
```