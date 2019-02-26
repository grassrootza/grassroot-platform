package za.org.grassroot.webapp.controller.rest;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Created by paballo on 2016/01/27.
 */
@Slf4j @Controller
@RequestMapping("/emulator/ussd/")
@Profile(value = {"localpg", "staging"})
public class USSDEmulatorController extends BaseController {

    private static final String phoneNumberParam = "msisdn";
    private static final String inputStringParam = "request";
    private static final String linkParam = "link";

    private final Environment environment;

    public USSDEmulatorController(UserManagementService userManagementService, PermissionBroker permissionBroker, Environment environment) {
        super(userManagementService, permissionBroker);
        this.environment = environment;
    }

    private String getBaseUrl() {
		return environment.acceptsProfiles(Profiles.of(GrassrootApplicationProfiles.STAGING)) ? "https://staging.grassroot.org.za/" : "http://localhost:8080/";
	}

    @RequestMapping(value = "view/{userPhone}", method = RequestMethod.GET)
    public String emulateUSSD(Model model, @PathVariable String userPhone,
                              @RequestParam(value = linkParam, required = false) String link,
                              @RequestParam(value = inputStringParam, required = false) String inputString) {
	    if (link == null) {
            link = getBaseUrl().concat("ussd/start");
        }

        URI targetUrl = getURI(link, userPhone, inputString);

        try {
            model.addAttribute("url", targetUrl.toURL());
        } catch (MalformedURLException e) {
            log.error("Error with URL: ", e);
        }

        try {
            Request request = getRequestObject(targetUrl);
	        if (request != null) {
		        boolean hasOptions= request.options != null && !request.options.isEmpty();
		        boolean display = !hasOptions || ((request.options.get(0).display == null) ? true : request.options.get(0).display);
		        model.addAttribute("userPhone", userPhone);
		        model.addAttribute("display", display);
		        model.addAttribute("request", request);
		        return "emulator/view";
	        } else {
		        return "emulator/error";
	        }
        } catch (Exception e) {
            log.error("Error in emulator!", e);
	        return "emulator/error";
        }
    }

    private Request getRequestObject(URI url) {
        Request returnedObject;
	    try {
            NullHostnameVerifier verifier = new NullHostnameVerifier();
            MySimpleClientHttpRequestFactory requestFactory = new MySimpleClientHttpRequestFactory(verifier);
            RestTemplate template = new RestTemplate();
            template.setRequestFactory(requestFactory);
            log.info("url: {}", url);
		    returnedObject = template.getForObject(url, Request.class);
	    } catch (RestClientException e) {
		    returnedObject = null;
		    log.error("Error with rest client", e);
	    } catch (Exception e) {
	        returnedObject = null;
	        log.error("Generic error in emulator", e);
        }
	    return returnedObject;
    }

    private URI getURI(String link, String phoneNumber, String inputString) {
        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(link);
            builder.addParameter(phoneNumberParam, phoneNumber);
            builder.addParameter(inputStringParam, inputString);
            uri = builder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }


    private class MySimpleClientHttpRequestFactory extends SimpleClientHttpRequestFactory {
        private final HostnameVerifier verifier;

        public MySimpleClientHttpRequestFactory(HostnameVerifier verifier) {
            this.verifier = verifier;

        }

        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(verifier);
                ((HttpsURLConnection) connection).setSSLSocketFactory(trustSelfSignedSSL().getSocketFactory());
            }
            super.prepareConnection(connection, httpMethod);
        }

        public SSLContext trustSelfSignedSSL() {
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                X509TrustManager tm = new X509TrustManager() {

                    public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                    }

                    public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                };
                ctx.init(null, new TrustManager[]{tm}, null);
                SSLContext.setDefault(ctx);
                return ctx;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        }


    }

    private class NullHostnameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }


    }
}
