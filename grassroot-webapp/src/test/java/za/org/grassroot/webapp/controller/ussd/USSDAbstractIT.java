package za.org.grassroot.webapp.controller.ussd;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.user.UserManagementService;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2015/09/10.
 * The USSD tests rely heavily on some common functions for piecing together and calling URLs, so am collecting them
 * all in one place here. If this starts impacting test suite performance can undo.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class USSDAbstractIT {

    private Logger log = LoggerFactory.getLogger(USSDAbstractIT.class);

    @Autowired
    protected UserManagementService userManager;

    @Autowired
    protected EventBroker eventBroker;

    @Autowired
    protected EventLogBroker eventLogManager;

    @Autowired
    protected TestRestTemplate template;

    @Value("${grassroot.http.port}")
    protected int port;

    protected UriComponentsBuilder base = UriComponentsBuilder.newInstance().scheme("https").host("localhost").port(port);

    // Common parameters for assembling the USSD urls
    protected final String ussdPath = "ussd/";
    protected final String userPath = "user/";

    // Common parameters used for assembling the USSD service calls
    protected final String phoneParam = "msisdn";
    protected final String freeTextParam = "request";

    // Some strings used throughout tests
    protected final String testPhone = "27815550000"; // todo: make sure this isn't an actual number
    protected final String testDisplayName = "TestPhone1";
    protected final String testPhoneZu = "27720000000"; // for testing the Zulu opening menu

    @PostConstruct
    public void init() {
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

    @Test
    public void test() throws Exception{

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
}
