package za.org.grassroot.webapp.controller.ussd;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by luke on 2015/09/10.
 * The USSD tests rely heavily on some common functions for piecing together and calling URLs, so am collecting them
 * all in one place here. If this starts impacting test suite performance can undo.
 */
public class USSDAbstractTest {

    @Value("${local.server.port}")
    int port;

    protected RestTemplate template = new TestRestTemplate();
    protected UriComponentsBuilder base = UriComponentsBuilder.newInstance().scheme("http").host("localhost");

    protected final String ussdPath = "ussd/";

    protected final String phoneParam = "msisdn";
    protected final String freeTextParam = "request";

    protected final String testPhone = "27815550000"; // todo: make sure this isn't an actual number
    protected final String onceOffPhone= "27805550000"; // slightly different ot main testPhone so rename doesn't break XML checks if renamed already
    protected final String testDisplayName = "TestPhone1";
    protected final List<String> testPhones = Arrays.asList("0825550000", "0835550000", "0845550000"); // todo: as above
    protected final Integer testGroupSize = testPhones.size() + 1; // includes creating user

    protected UriComponentsBuilder assembleTestURI(String urlEnding) {
        UriComponentsBuilder baseUri = UriComponentsBuilder.fromUri(base.build().toUri())
                .path(ussdPath + urlEnding);
        return baseUri;
    }

    protected UriComponentsBuilder testPhoneUri(String urlEnding) {
        return assembleTestURI(urlEnding).queryParam(phoneParam, testPhone);
    }

    protected List<ResponseEntity<String>> executeQueries(List<URI> urisToExecute) {
        List<ResponseEntity<String>> responseEntities = new ArrayList<>();
        for (URI uriToExecute : urisToExecute) {
            responseEntities.add(template.getForEntity(uriToExecute, String.class));
        }
        return responseEntities;
    }

    protected ResponseEntity<String> executeQuery(URI uriToExecute) {
        return template.getForEntity(uriToExecute, String.class);
    }

}
