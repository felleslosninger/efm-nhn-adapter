# efm-nhn-adapter

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
