package za.org.grassroot.webapp.controller.ussd;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.GrassRootWebApplicationConfig;

import javax.annotation.PostConstruct;
import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by luke on 2015/09/10.
 * The USSD tests rely heavily on some common functions for piecing together and calling URLs, so am collecting them
 * all in one place here. If this starts impacting test suite performance can undo.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {GrassRootWebApplicationConfig.class})
@WebIntegrationTest(randomPort = true)
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class USSDAbstractIT {

    private Logger log = LoggerFactory.getLogger(USSDAbstractIT.class);

    @Autowired
    protected UserManagementService userManager;

    @Autowired
    protected GroupManagementService groupManager;

    @Autowired
    protected EventManagementService eventManager;

    @Autowired
    protected EventLogManagementService eventLogManager;

    @Value("${local.server.port}")
    protected int port;

    //protected RestTemplate template = new TestRestTemplate(TestRestTemplate.HttpClientOption.ENABLE_REDIRECTS);
    protected RestTemplate template = new TestRestTemplate();
    protected UriComponentsBuilder base = UriComponentsBuilder.newInstance().scheme("https").host("localhost").port(port);

    // Common parameters for assembling the USSD urls
    protected final String ussdPath = "ussd/";
    protected final String mtgPath = "mtg/";
    protected final String userPath = "user/";

    // Common parameters used for assembling the USSD service calls
    protected final String phoneParam = "msisdn";
    protected final String freeTextParam = "request";
    protected final String eventParam = "eventId";

    // Some strings used throughout tests
    protected final String testPhone = "27815550000"; // todo: make sure this isn't an actual number
    protected final String secondGroupPhone = "27625550000"; // slightly different ot main testPhone so rename doesn't break XML checks if renamed already
    protected final String thirdGroupPhone = "27835550000";
    protected final String testDisplayName = "TestPhone1";
    protected final List<String> testPhones = Arrays.asList(PhoneNumberUtil.invertPhoneNumber(secondGroupPhone, ""),
                                                            PhoneNumberUtil.invertPhoneNumber(thirdGroupPhone, ""), "0845550000"); // todo: as above
    protected final Integer testGroupSize = testPhones.size() + 1; // includes creating user

    protected final String nonGroupPhone = "27800000000";
    protected final String testPhoneZu = "27720000000"; // for testing the Zulu opening menu
    protected final String testMtgLocation = "JoziHub";
    protected final String testMtgDateTime = "Tomorrow 9am";

    @PostConstruct
    public void init() {
        final MySimpleClientHttpRequestFactory factory = new MySimpleClientHttpRequestFactory(
                new HostnameVerifier() {

                    @Override
                    public boolean verify(final String hostname,
                                          final SSLSession session) {
                        return true; // these guys are alright by me...
                    }
                });
        template.setRequestFactory(factory);
    }

    @BeforeClass
    public static void classSetUp() {
        // setup ssl context to ignore certificate errors

        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            X509TrustManager tm = new X509TrustManager() {

                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                               String authType) throws java.security.cert.CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                               String authType) throws java.security.cert.CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };
            ctx.init(null, new TrustManager[] { tm }, null);
            SSLContext.setDefault(ctx);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    protected UriComponentsBuilder assembleUssdURI(String urlEnding) {
        UriComponentsBuilder baseUri = UriComponentsBuilder.fromUri(base.build().toUri())
                .path(ussdPath + urlEnding);
        return baseUri;
    }

    protected UriComponentsBuilder testPhoneUri(String urlEnding) {
        return assembleUssdURI(urlEnding).queryParam(phoneParam, testPhone);
    }

    protected List<ResponseEntity<String>> executeQueries(List<URI> urisToExecute) {
        List<ResponseEntity<String>> responseEntities = new ArrayList<>();
        for (URI uriToExecute : urisToExecute) {
            log.trace("before calling..." + uriToExecute);
            responseEntities.add(template.getForEntity(uriToExecute, String.class));
        }
        return responseEntities;
    }

    protected ResponseEntity<String> executeQuery(URI uriToExecute) {
        return template.getForEntity(uriToExecute, String.class);
    }

    protected URI testPhoneUriBuild(String path) {
        return testPhoneUri(path).build().toUri();
    }

    protected User createTestUser() {
        executeQuery(testPhoneUriBuild("start"));
        return userManager.findByInputNumber(testPhone);
    }

    /**
     * Http Request Factory for ignoring SSL hostname errors. Not for production use!
     */
    class MySimpleClientHttpRequestFactory extends SimpleClientHttpRequestFactory {

        private final HostnameVerifier verifier;

        public MySimpleClientHttpRequestFactory(final HostnameVerifier verifier) {
            this.verifier = verifier;
        }

        @Override
        protected void prepareConnection(final HttpURLConnection connection,
                                         final String httpMethod) throws IOException {
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(this.verifier);
            }
            super.prepareConnection(connection, httpMethod);
        }
    }

}
