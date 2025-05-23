package no.difi.meldingsutveksling.nhn.adapter;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import no.ks.fiks.helseid.HelseIdClient;
import no.ks.fiks.helseid.dpop.Endpoint;
import no.ks.fiks.helseid.dpop.HttpMethod;
import no.ks.fiks.helseid.dpop.ProofBuilder;

@RestController
public class TestController {

    @Autowired
    private HelseIDClientConfig config;

    @Autowired
    private HelseIdClient helseIdClient;

    @Autowired
    private HttpClient httpClient;

    @GetMapping(path = "/hello")
    public String test() {
        return "Hello World" + config.getClientID().length() + "," + config.getJwkKey().length();
    }

    @GetMapping(path = "/testHelseId")
    public String testHelseId() {
        return helseIdClient.getAccessToken().getAccessToken();
    }

    @GetMapping(path = "/testFlr")
    public String getFlrContracts() throws Exception {
        String dpopAccessToken = helseIdClient.getDpopAccessToken().getAccessToken();
        
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

}
