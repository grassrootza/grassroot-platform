package za.org.grassroot.webapp.controller.rest;

import org.apache.http.client.utils.URIBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
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
 */

@Controller
@RequestMapping("/emulator/")
public class USSDEmulatorController extends BaseController {

    private static final String BASE = "https://localhost:8443/";
    private static final String phoneNumberParam = "msisdn";
    private static final String inputStringParam = "request";
    private static final String linkParam = "link";
 

    @RequestMapping(value = "view", method = RequestMethod.GET)
    public String emulateUSSD(Model model, @RequestParam(value = linkParam, required = false) String link,
                               @RequestParam(value = inputStringParam, required = false) String inputString) {

        if (link == null) {
            link = BASE.concat("ussd/start");
        }
        //todo: fix this
        link = link.replace("http://localhost:8080", "https://localhost:8443");
        URI targetUrl = getURI(link, inputString);
        Request request = getRequestObject(targetUrl);

        boolean display = (request.options.get(0).display == null) ? true : request.options.get(0).display;
        model.addAttribute("display", display);
        model.addAttribute("request", request);
        try {
            model.addAttribute("url", targetUrl.toURL());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return "emulator/view";
    }


    private Request getRequestObject(URI url) {

        NullHostnameVerifier verifier = new NullHostnameVerifier();
        MySimpleClientHttpRequestFactory requestFactory = new MySimpleClientHttpRequestFactory(verifier);
        RestTemplate template = new RestTemplate();
        template.setRequestFactory(requestFactory);

        return template.getForObject(url, Request.class);

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
