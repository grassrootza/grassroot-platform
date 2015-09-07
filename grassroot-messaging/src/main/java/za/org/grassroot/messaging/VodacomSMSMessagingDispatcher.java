package za.org.grassroot.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.messaging.domain.Destination;
import za.org.grassroot.messaging.domain.MessagePublishRequest;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
@Component("vodacomSMSMessaging")
public class VodacomSMSMessagingDispatcher {

    private Logger log = LoggerFactory.getLogger(VodacomSMSMessagingDispatcher.class);

    private String smsGatewayHost = "xml2sms.gsm.co.za";
    private String smsGatewayUsername = System.getenv("SMSUSER");
    private String smsGatewayPassword = System.getenv("SMSPASS");

    public void sendMessage(MessagePublishRequest messagePublishRequest)
    {

        RestTemplate restTemplate = new RestTemplate();

        UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance().scheme("https").host(smsGatewayHost);
        gatewayURI.path("send/").queryParam("username", smsGatewayUsername).queryParam("password", smsGatewayPassword);

        List<String> phoneNumbers = messagePublishRequest.getDestination().getToAddresses();

        for (int index = 1; index <= phoneNumbers.size(); index++) {

            gatewayURI.queryParam(String.join("number", String.valueOf(index)), phoneNumbers.get(index - 1));
            gatewayURI.queryParam(String.join("message", String.valueOf(index), messagePublishRequest.getMessage().getBody()));

            //@todo process response message

            String messageResult = restTemplate.getForObject(gatewayURI.build().toUri(), String.class);
        }

    }
}
