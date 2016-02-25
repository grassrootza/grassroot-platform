package za.org.grassroot.integration.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.UserRepository;

/**
 * Created by luke on 2015/09/09.
 */
@Service
public class SmsSendingManager implements SmsSendingService {

    // todo: add error and exception handling

    private Logger log = LoggerFactory.getLogger(SmsSendingManager.class);

    @Autowired
    Environment environment;

    /*
     Would have preferred eventLogManagement than the repository, but that creates circular dependency. Note that we
      only use this, at present, for the tests.
      */
    @Autowired
    EventLogRepository eventLogRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    private String smsGatewayHost = "xml2sms.gsm.co.za";
    private String smsGatewayUsername = System.getenv("SMSUSER");
    private String smsGatewayPassword = System.getenv("SMSPASS");

    private String testMessagePhone = "27701110000";

    @Override
    public String sendSMS(String message, String destinationNumber) {

        RestTemplate restTemplate = new RestTemplate();

        UriComponentsBuilder gatewayURI = UriComponentsBuilder.newInstance().scheme("https").host(smsGatewayHost);

        gatewayURI.path("send/").queryParam("username", smsGatewayUsername).queryParam("password", smsGatewayPassword);
        gatewayURI.queryParam("number", destinationNumber);
        gatewayURI.queryParam("message", message);

        // todo: test this on staging
        if (!environment.acceptsProfiles(GrassRootApplicationProfiles.INMEMORY)) {
            log.info("Sending SMS via URL: " + gatewayURI.toUriString());
            //@todo process response message
            String messageResult = restTemplate.getForObject(gatewayURI.build().toUri(), String.class);
            log.info("SMS...result..." + messageResult);
            return messageResult;
        } else {
            // todo: store this result somewhere in the cache so an integrated test can check it
            User testMessageUser = (userRepository.existsByPhoneNumber(testMessagePhone)) ?
                    userRepository.findByPhoneNumber(testMessagePhone) :
                    userRepository.save(new User(testMessagePhone));
            Event messageEvent = eventRepository.save(new Event(testMessageUser, EventType.DummyEvent));
            EventLog messageRecord = new EventLog(testMessageUser, messageEvent, EventLogType.EventTest, message);
            log.info("Saving a dummy EventLog ... " + messageRecord);
            messageRecord = eventLogRepository.save(messageRecord);
            log.info("EventLog saved with message: " + messageRecord.getMessage());
            return messageRecord.toString();
        }
    }

}
