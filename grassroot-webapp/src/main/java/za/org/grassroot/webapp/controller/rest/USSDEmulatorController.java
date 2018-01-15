package za.org.grassroot.webapp.controller.rest;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
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
 * note : as is appropriate, only works if ussd gateway is set to localhost IP (else security rejects calls)
 */

@Controller
@RequestMapping("/emulator/ussd/")
public class USSDEmulatorController extends BaseController {

	private static final Logger logger = LoggerFactory.getLogger(USSDEmulatorController.class);

	private final Environment environment;

    private static final String phoneNumberParam = "msisdn";
    private static final String inputStringParam = "request";
    private static final String linkParam = "link";

    @Autowired
    public USSDEmulatorController(Environment environment) {
        this.environment = environment;
    }

    private String getBaseUrl() {
		if (environment.acceptsProfiles("staging")) {
			return "https://staging.grassroot.org.za:443/";
		} else {
			return "http://localhost:8080/";
		}
	}

    @RequestMapping(value = "view", method = RequestMethod.GET)
    public String emulateUSSD(Model model, @RequestParam(value = linkParam, required = false) String link,
                               @RequestParam(value = inputStringParam, required = false) String inputString) {

	    // note : this should not be accessible on production environment, hence ...
	    if (environment.acceptsProfiles("production")) {
		    throw new AccessDeniedException("Error! Emulator not accessible on production");
	    }

        if (link == null) {
            link = getBaseUrl().concat("ussd/start");
        }

        URI targetUrl = getURI(link, inputString);

        try {
            model.addAttribute("url", targetUrl.toURL());
        } catch (MalformedURLException e) {
            logger.error("Error with URL: ", e);
        }

        try {
            logger.info("About to get request object ...");
	        Request request = getRequestObject(targetUrl);
	        if (request != null) {
		        boolean display;
		        if (request.options != null & !request.options.isEmpty()) {
			        display = (request.options.get(0).display == null) ? true : request.options.get(0).display;
		        } else {
			        display = true;
		        }
		        model.addAttribute("display", display);
		        model.addAttribute("request", request);
		        return "emulator/view";
	        } else {
		        return "emulator/error";
	        }
        } catch (Exception e) {
            logger.error("Error in emulator!", e);
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
            logger.info("url: {}", url);
		    returnedObject = template.getForObject(url, Request.class);
	    } catch (RestClientException e) {
		    returnedObject = null;
		    logger.error("Error with rest client", e);
	    } catch (Exception e) {
	        returnedObject = null;
	        logger.error("Generic error in emulator", e);
        }
	    return returnedObject;
    }

    private URI getURI(String link, String inputString) {
        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(link);
            builder.addParameter(phoneNumberParam, getUserProfile().getPhoneNumber());
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
