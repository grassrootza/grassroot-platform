package za.org.grassroot.integration.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.integration.JwtService;
import za.org.grassroot.integration.sms.aat.AatResponseInterpreter;
import za.org.grassroot.integration.sms.aat.AatSmsResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;

/**
 * Created by luke on 2015/09/09.
 */
@Service
public class SmsSendingManager implements SmsSendingService {

    private Logger log = LoggerFactory.getLogger(SmsSendingManager.class);

    @Autowired
    private Environment environment;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

    private String smsGatewayHost;
    private String smsGatewayUsername;
    private String smsGatewayPassword;

    private String smsPriorityUsername;
    private String smsPriorityPassword;

    @PostConstruct
    public void init() {
        smsGatewayHost = environment.getRequiredProperty("grassroot.sms.gateway", String.class);
        smsGatewayUsername = environment.getRequiredProperty("grassroot.sms.gateway.username", String.class);
        smsGatewayPassword = environment.getRequiredProperty("grassroot.sms.gateway.password", String.class);

        smsPriorityUsername = environment.getProperty("SMSPUSER", smsGatewayUsername);
        smsPriorityPassword = environment.getProperty("SMSPPASS", smsGatewayPassword);
    }

    @Override
    public SmsGatewayResponse sendSMS(String message, String destinationNumber) {
        UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance().scheme("https").host(smsGatewayHost);

        gatewayURI.path("send/").queryParam("username", smsGatewayUsername).queryParam("password", smsGatewayPassword);
        gatewayURI.queryParam("number", destinationNumber);
        gatewayURI.queryParam("message", message);

        if (!environment.acceptsProfiles("default")) {
            SmsGatewayResponse response = new AatResponseInterpreter(restTemplate.getForObject(gatewayURI.build().toUri(), AatSmsResponse.class));
            log.info("SMS...result... as string = {}", response.toString());
            return response;
        } else {
            return null;
        }
    }

    @Override
    public SmsGatewayResponse sendPrioritySMS(String message, String destinationNumber) {
        /*UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance().scheme("https").host(smsGatewayHost);

        gatewayURI.path("send/").queryParam("username", smsPriorityUsername).queryParam("password", smsPriorityPassword);
        gatewayURI.queryParam("number", destinationNumber);
        gatewayURI.queryParam("message", message);

        SmsGatewayResponse response = new AatResponseInterpreter(restTemplate.getForObject(gatewayURI.build().toUri(), AatSmsResponse.class));
        log.info("Priority SMS sent, with result ... " + response.toString());*/
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtService.createJwt(new HashMap<>()));
        HttpEntity<AatSmsResponse> httpEntity = new HttpEntity<>(headers);
        UriComponentsBuilder servicesUri = UriComponentsBuilder
                .fromUriString("http://localhost:8081/notification/push/priority/" + destinationNumber)
                .queryParam("message", message);
        ResponseEntity<AatSmsResponse> response = restTemplate.exchange(
                servicesUri.toUriString(),
                HttpMethod.POST,
                httpEntity,
                AatSmsResponse.class);
        return (SmsGatewayResponse) response.getBody();
    }

    @Async
    @Override
    public void sendAsyncSMS(String message, String destinationNumber) {
        sendSMS(message, destinationNumber);
    }

}
