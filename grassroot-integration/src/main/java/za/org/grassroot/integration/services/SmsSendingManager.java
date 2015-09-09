package za.org.grassroot.integration.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Created by luke on 2015/09/09.
 */
@Service
public class SmsSendingManager implements SmsSendingService {

    // todo: add error and exception handling

    private Logger log = LoggerFactory.getLogger(SmsSendingManager.class);

    private String smsGatewayHost = "xml2sms.gsm.co.za";
    private String smsGatewayUsername = System.getenv("SMSUSER");
    private String smsGatewayPassword = System.getenv("SMSPASS");

    @Override
    public String sendSMS(String message, String destinationNumber) {

        RestTemplate restTemplate = new RestTemplate();

        UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance().scheme("https").host(smsGatewayHost);

        gatewayURI.path("send/").queryParam("username", smsGatewayUsername).queryParam("password", smsGatewayPassword);
        gatewayURI.queryParam("number", destinationNumber);
        gatewayURI.queryParam("message", message);

        log.info("Sending SMS via URL: " + gatewayURI.toUriString());

        //@todo process response message

        String messageResult = restTemplate.getForObject(gatewayURI.build().toUri(), String.class);
        return messageResult;

    }

}
