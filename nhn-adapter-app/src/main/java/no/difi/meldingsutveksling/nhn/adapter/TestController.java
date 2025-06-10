package no.difi.meldingsutveksling.nhn.adapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import no.ks.fiks.hdir.Helsepersonell;
import no.ks.fiks.hdir.HelsepersonellsFunksjoner;
import no.ks.fiks.hdir.OrganisasjonIdType;
import no.ks.fiks.helseid.HelseIdClient;
import no.ks.fiks.helseid.dpop.Endpoint;
import no.ks.fiks.helseid.dpop.HttpMethod;
import no.ks.fiks.helseid.dpop.ProofBuilder;
import no.ks.fiks.nhn.edi.BusinessDocumentSerializer;
import no.ks.fiks.nhn.msh.BusinessDocumentMessage;
import no.ks.fiks.nhn.msh.Client;
import no.ks.fiks.nhn.msh.DialogmeldingVersion;
import no.ks.fiks.nhn.msh.HealthcareProfessional;
import no.ks.fiks.nhn.msh.HerIdReceiver;
import no.ks.fiks.nhn.msh.HerIdReceiverParent;
import no.ks.fiks.nhn.msh.Id;
import no.ks.fiks.nhn.msh.OrganizationHerIdReceiverChild;
import no.ks.fiks.nhn.msh.OutgoingBusinessDocument;
import no.ks.fiks.nhn.msh.Patient;
import no.ks.fiks.nhn.msh.RecipientContact;
import no.ks.fiks.nhn.msh.Vedlegg;

@RestController
public class TestController {

    @Autowired
    private HelseIDClientConfig config;

    @Autowired
    private HelseIdClient helseIdClient;
    
    @Autowired
    private Client nhnClient;

    @Autowired
    private HttpClient httpClient;

    @GetMapping(path = "/hello")
    public String test() {
        return "Hello World" + config.getClientID().length() + "," + config.getJwkKey().length();
    }

    @GetMapping(path = "/testHelseId")
    public String testHelseId() {
        return helseIdClient.getAccessToken(null,null).getAccessToken();
    }

    @GetMapping(path = "/testFlr")
    public String getFlrContracts() throws Exception {
        String dpopAccessToken = helseIdClient.getDpopAccessToken(null,null).getAccessToken();
        
        var endpoint = new Endpoint(HttpMethod.GET, "https://api.offentlig.test.flr.nhn.no/contracts");

        ProofBuilder proofBuilder = new ProofBuilder(config.helseIdConfiguration());

        var dpopProof = proofBuilder.buildProof(endpoint, null, dpopAccessToken);

        HttpGet request = new HttpGet("https://api.offentlig.test.flr.nhn.no/contracts");
        request.setHeader("Authorization", "DPoP " + dpopAccessToken);
        request.setHeader("DPoP", dpopProof);

        HttpClientResponseHandler<String> responseHandler = response -> {
            int status = response.getCode();
            HttpEntity entity = response.getEntity();
            if (status >= 200 && status < 300) {
                return entity != null ? EntityUtils.toString(entity) : null;
            } else {
                throw new ClientProtocolException("Unexpected response status: " + status);
            }
        };

        String responseBody = httpClient.execute(request, responseHandler);
        return responseBody;
    }
    
    private Boolean hasParentOrgClaimWithValue(String token, String enhetValue)  {
    	 try {
             SignedJWT signedJWT = SignedJWT.parse(token);
             JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

             // Check if the claim exists
             Object claimValue = claimsSet.getClaim("helseid://claims/client/claims/orgnr_parent");
             return claimValue != null;

         } catch (java.text.ParseException e) {
             e.printStackTrace();
             return false;
         }
    }
    
    @GetMapping(path = "/testSendInnOgMotta")
    public String testEDI20SendOgMotta() {
    		StringWriter writer = new StringWriter();
    		PrintWriter printer = new PrintWriter(writer);
    		String avsenderEnhet = "931796003";
    		String accessToken = helseIdClient.getAccessToken(avsenderEnhet,null).getAccessToken();
    		
    		printer.println("Henting av access token på veien av" + avsenderEnhet + (hasParentOrgClaimWithValue(accessToken, avsenderEnhet) ? "OK": " NOT OK"));
    		String dpopAccessToken = helseIdClient.getDpopAccessToken("931796003",null).getAccessToken();
    		printer.println("Henting av dpop token på veien av" + avsenderEnhet + (hasParentOrgClaimWithValue(dpopAccessToken, avsenderEnhet) ? "OK": " NOT OK"));
    		
    		 OutgoingBusinessDocument outgoingBusinessDocument = new OutgoingBusinessDocument(UUID.randomUUID(),
    	        		new no.ks.fiks.nhn.msh.Organization("KS-DIGITALE FELLESTJENESTER AS", new Id("8142987", OrganisasjonIdType.HER_ID), new no.ks.fiks.nhn.msh.Organization("Digdir multi-tenant test", new Id("8143154", OrganisasjonIdType.HER_ID), null)),
    	        		new HerIdReceiver( new HerIdReceiverParent("DIGITALISERINGSDIREKTORATET", new Id("8143143", OrganisasjonIdType.HER_ID)), new OrganizationHerIdReceiverChild("Service 1", new Id("8143144", OrganisasjonIdType.HER_ID)), new Patient("14038342168", "Aleksander", null, "Petterson")),
    	        		new BusinessDocumentMessage(
    	                        "<Message subject>",
    	                        "<Message body>",
    	                        new HealthcareProfessional(
    	                            // Person responsible for this message at the sender
    	                            "<First name>",
    	                            "<Middle name>",
    	                            "<Last name>",
    	                            "11223344",
    	                            HelsepersonellsFunksjoner.HELSEFAGLIG_KONTAKT // This persons role with respect to the patient
    	                        ),
    	                        new RecipientContact(
    	                           
    	                            Helsepersonell.LEGE // Professional group of the healthcare professional recieving the message
    	                        )
    	                    ), new Vedlegg(OffsetDateTime.now(), "<Description of the attachment>", this.getClass().getClassLoader().getResourceAsStream("small.pdf")), 
    	        		DialogmeldingVersion.V1_1);
    	    try {
    	    	BusinessDocumentSerializer.INSTANCE.serializeNhnMessage(outgoingBusinessDocument);
    	    	printer.println("Oprettelse og serializering av OutgoingBusinessDocument er: OK");
    	    }
    	    catch(Exception e) {
    	    	printer.println("Oprettelse og serializering av OutgoingBusinessDocument er: NOT OK");
    	    }
    		
    	    try {
    	    	nhnClient.sendMessage(outgoingBusinessDocument,avsenderEnhet);
    	    	printer.println("Sending dialogmelding med vedleg:" + "OK");
        		
    	    }
    	    catch (Exception e) {
				printer.println("Sending dialogmelding med vedleg:" + "NOT OK");
			}
    	    try {
    	    	nhnClient.sendMessage(outgoingBusinessDocument,avsenderEnhet);
    	    	printer.println("Sending dialogmelding med vedlegg på veien av" + avsenderEnhet  + ":" + "OK");
    	    	
    	    }catch(Exception e) {
    	    	printer.println("Sending dialogmelding med vedleg på veien av" + avsenderEnhet  + ":" +  "NOT OK");
    	    }
    	    var mottakerEnhet = "991825827";
    	    
    	    try {
    	    	  nhnClient.getMessages(8143144,avsenderEnhet).iterator().next().getId();
    	    	  printer.println("Recieving på veien av avsender. Skall få 403:" + "NOT OK");
    	    	
    	    }catch(Exception e) {
    	    	printer.println("Recieving på veien av avsender. Skall få 403:" + "OK");
    	    }
    	    UUID messageID = null;
    	    try {
    	    	  messageID = nhnClient.getMessages(8143144,mottakerEnhet).iterator().next().getId();
    	    	  printer.println("Recieving på veien av mottaker: " + "OK");
    	    	 
			} catch (Exception e) {
				 printer.println("Recieving på veien av mottaker: " + "NOT OK");
			}
    	    try {
    	    	  nhnClient.getBusinessDocument(messageID,mottakerEnhet);
    	    	  printer.println("Get business dokument på veien av mottaker: " + "OK");
    	    }catch (Exception e) {
    	    	printer.println("Get business dokument på veien av mottaker: " + " NOT OK");
			}
    	    try {
    	    	nhnClient.markMessageRead(messageID, 8143144 , mottakerEnhet);
    	    	printer.println("Mark message as read på veien av mottaker: " + "OK");
			} catch (Exception e) {
				printer.println("Mark message as read på veien av mottaker: " + "Not OK");
			}
    	      
    	   
    		return writer.toString();

    }

}
