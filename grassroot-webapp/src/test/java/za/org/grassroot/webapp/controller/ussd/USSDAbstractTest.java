package za.org.grassroot.webapp.controller.ussd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;

import java.net.URI;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by luke on 2015/09/10.
 * The USSD tests rely heavily on some common functions for piecing together and calling URLs, so am collecting them
 * all in one place here. If this starts impacting test suite performance can undo.
 */
public class USSDAbstractTest {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    UserManagementService userManager;

    @Autowired
    GroupManagementService groupManager;

    @Autowired
    EventManagementService eventManager;

    @Autowired
    EventLogManagementService eventLogManager;

    @Value("${local.server.port}")
    int port;

    protected RestTemplate template = new TestRestTemplate();
    protected UriComponentsBuilder base = UriComponentsBuilder.newInstance().scheme("http").host("localhost");

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
    protected final String secondGroupPhone = "27825550000"; // slightly different ot main testPhone so rename doesn't break XML checks if renamed already
    protected final String thirdGroupPhone = "27835550000";
    protected final String testDisplayName = "TestPhone1";
    protected final List<String> testPhones = Arrays.asList(PhoneNumberUtil.invertPhoneNumber(secondGroupPhone, ""),
                                                            PhoneNumberUtil.invertPhoneNumber(thirdGroupPhone, ""), "0845550000"); // todo: as above
    protected final Integer testGroupSize = testPhones.size() + 1; // includes creating user

    protected final String nonGroupPhone = "27805550000";
    protected final String testPhoneZu = "27725550000"; // for testing the Zulu opening menu
    protected final String testMtgLocation = "JoziHub";
    protected final String testMtgDateTime = "Tomorrow 9am";

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
            log.finest("before calling..." + uriToExecute);
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

    protected Group createTestGroup() {

        // possibly redundant to create test user, but don't want to generate false fails depending on how this is called
        User testUser = createTestUser();
        final URI createGroupUri = testPhoneUri("group/create-do").queryParam(freeTextParam, String.join(" ", testPhones)).
                build().toUri();

        executeQuery(createGroupUri);
        Group groupToReturn = groupManager.getLastCreatedGroup(testUser);

        return groupToReturn;

    }

    protected Event createTestMeeting() {

        User testUser = createTestUser();
        Group testGroup = createTestGroup();

        // by default, our test event does not include sub-groups but does ask for an RSVP;
        Event eventToTest = eventManager.createEvent("testEvent", testUser, testGroup, false, true);
        Long eventId = eventToTest.getId();

        eventToTest = eventManager.setLocation(eventId, testMtgLocation);
        eventToTest = eventManager.setDateTimeString(eventId, testMtgDateTime);
        eventToTest = eventManager.setEventTimestamp(eventId, Timestamp.valueOf(DateTimeUtil.parseDateTime(testMtgDateTime)));

        return eventToTest;

    }

}
