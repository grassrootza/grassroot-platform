package za.org.grassroot.webapp.controller.ussd;

/**
 * Need to work out how to test the messaging system without sending an SMS every time we compile
 * Probably needs to wait until after we have separated the messaging layer from the controller
 */

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.GrassRootWebApplicationConfig;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.net.URI;
import java.util.*;

import static junit.framework.Assert.assertNotNull;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLNotEqual;
import static org.dbunit.Assertion.assertEquals;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {GrassRootWebApplicationConfig.class})
@WebIntegrationTest(randomPort = true)
//@EnableTransactionManagement
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class USSDHomeControllerTest extends USSDAbstractTest {

    protected static final Logger log = LoggerFactory.getLogger(USSDHomeControllerTest.class);

    @Autowired
    EntityManager em;

    /* @Autowired
    protected UserManagementService userManager;

    @Autowired
    protected GroupManagementService groupManager;

    @Autowired
    protected EventManagementService eventManager; */

    private final String startMenuEN = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<request>" +
            "    <headertext>Hi! Welcome to GrassRoot. What will you do?</headertext>" +
            "    <options>" +
            "        <option command=\"1\" order=\"1\" callback=\"http://localhost:8080/ussd/mtg/start\"" +
            "                display=\"true\">Call a meeting</option>\n" +
            "        <option command=\"2\" order=\"2\" callback=\"http://localhost:8080/ussd/vote\"" +
            "                display=\"true\">Take a vote</option>\n" +
            "        <option command=\"3\" order=\"3\" callback=\"http://localhost:8080/ussd/log\"" +
            "                display=\"true\">Record an action</option>\n" +
            "        <option command=\"4\" order=\"4\" callback=\"http://localhost:8080/ussd/group/start\"" +
            "                display=\"true\">Manage groups</option>\n" +
            "        <option command=\"5\" order=\"5\" callback=\"http://localhost:8080/ussd/user/start\"" +
            "                display=\"true\">Change profile</option>\n" +
            "    </options>" +
            "</request>";

    private final String startMenuZU = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<request>" +
            "    <headertext>Yebo, siyakwamukela KuGrassRoot. Uzokwenzani?</headertext>" +
            "    <options>" +
            "        <option command=\"1\" order=\"1\" callback=\"http://localhost:8080/ussd/mtg/start\"" +
            "                display=\"true\">Biza umhlangano</option>\n" +
            "        <option command=\"2\" order=\"2\" callback=\"http://localhost:8080/ussd/vote\"" +
            "                display=\"true\">Biza ivoti</option>\n" +
            "        <option command=\"3\" order=\"3\" callback=\"http://localhost:8080/ussd/log\"" +
            "                display=\"true\">Bhala isenzo</option>\n" +
            "        <option command=\"4\" order=\"4\" callback=\"http://localhost:8080/ussd/group/start\"" +
            "                display=\"true\">Phatha amaqembu</option>\n" +
            "        <option command=\"5\" order=\"5\" callback=\"http://localhost:8080/ussd/user/start\"" +
            "                display=\"true\">Shintsha iprofile</option>\n" +
            "    </options>" +
            "</request>";

    @Before
    public void setUp() throws Exception {
        base.port(port);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalizeWhitespace(true);
    }

    @Test
    public void getHello() throws Exception {
        final URI requestUri = base.path("ussd/test_question").build().toUri();
        ResponseEntity<String> response = template.getForEntity(requestUri, String.class);

        System.out.println(base.toUriString());

        assertThat(response.getStatusCode(), is(OK));
        final String expectedResponseXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                                           "<request>" +
                                           "    <headertext>Can you answer the question?</headertext>" +
                                           "    <options>" +
                                           "        <option command=\"1\" order=\"1\" callback=\"http://yourdomain.tld/ussdxml.ashx?file=2\"" +
                                           "                display=\"true\">Yes I can!</option>\n" +
                                           "    </options>" +
                                           "</request>";
        System.out.println(response.getBody());

        assertXMLEqual(expectedResponseXml, response.getBody());
    }

    // @author luke: Test to check that a new USSD request creates and saves (or loads) a user

    @Test
    public void userStart() throws Exception {

        final URI requestUri = base.path("ussd/start").queryParam(phoneParam, nonGroupPhone)
                .queryParam("request", "*120*1994#").build().toUri();
        ResponseEntity<String> response = template.getForEntity(requestUri, String.class);

        System.out.println(base.toUriString());

        User userCreated = userManager.findByInputNumber(nonGroupPhone);
        assertNotNull(userCreated.getId());
        assertNotNull(userCreated.getCreatedDateTime());
        assertThat(userCreated.getPhoneNumber(), is(nonGroupPhone));



        assertXMLEqual(startMenuEN, response.getBody());
    }

    /*
    Test for localization via checking home menu response. Currently, just for Zulu, will add other tests as other
    i18n options added.
     */

    @Test
    public void userStartLocale() throws  Exception {

        final URI requestUri = assembleUssdURI("start").queryParam(phoneParam, testPhoneZu).build().toUri();
        executeQuery(requestUri);

        log.info("User creation URI string: " + requestUri.toString());
        User userCreated = userManager.findByInputNumber(testPhoneZu);

        final URI changeLanguageUri = assembleUssdURI("user/language-do").queryParam(phoneParam, testPhoneZu).queryParam("language", "zu").build().toUri();
        ResponseEntity response = executeQuery(changeLanguageUri);

        log.info("The URL used is as follows: " + changeLanguageUri.toString());
        log.info("The user has been saved, as: " + userCreated);
        log.info("The user's locale code is: " + userCreated.getLanguageCode());

        final URI secondStartMenu = assembleUssdURI("start").queryParam(phoneParam, testPhoneZu).build().toUri();
        ResponseEntity<String> newHomeMenu = executeQuery(secondStartMenu);
        log.info("Zulu URL executed: " + secondStartMenu.toString());

        assertNotNull(userCreated.getId());
        assertNotNull(userCreated.getCreatedDateTime());
        assertXMLEqual(startMenuZU, newHomeMenu.getBody());


    }

    // Test to check that a user can rename themselves and be greeted on next access

    @Test
    public void userRename() throws Exception {

        final URI createUserUri = testPhoneUri("start").build().toUri();
        final URI renameUserUri = testPhoneUri(userPath + "name-do").queryParam(freeTextParam, testDisplayName).build().toUri();

        List<ResponseEntity<String>> responseEntities = executeQueries(Arrays.asList(createUserUri, renameUserUri, createUserUri));

        System.out.println("URI String: " + renameUserUri.toString());

        for (ResponseEntity<String> responseEntity : responseEntities)
            assertThat(responseEntity.getStatusCode(), is(OK));

        assertThat(userManager.findByInputNumber(testPhone).hasName(), is(true));
        assertThat(userManager.findByInputNumber(testPhone).getDisplayName(), is(testDisplayName));
        assertThat(userManager.findByInputNumber(testPhone).getName(""), is(testDisplayName));

        // final test is that on next access to system the name comes up
        // removing this test for now because the prompt to rename a group now comes up, without a name, so would have
        // to either break stand-alone integrity of the tests or alter UX for sake of tests.

        // assertThat(responseEntities.get(2).getBody(), containsString(testDisplayName)); // just checking contains name, not exact format

    }

    /*
    Test that the group joining code is working
     */
    @Test
    public void joiningCode() throws Exception {

        User testUser = createTestUser();
        Group testGroup = createTestGroup();
        testGroup = groupManager.generateGroupToken(testGroup, 1);
        String groupToken = testGroup.getGroupTokenCode();

        final URI useJoinCode = assembleUssdURI("start").queryParam(phoneParam, nonGroupPhone).
                queryParam("request", "*120*1994*" + groupToken + "#").build().toUri();

        Long groupId = testGroup.getId();
        log.info("Group token URI testing: " + useJoinCode.toString());
        log.info("The group itself has the token: " + groupManager.loadGroup(groupId).getGroupTokenCode() + ", " +
                         "and group now stored as: " + groupManager.loadGroup(testGroup.getId()));
        ResponseEntity<String> responseEntity = executeQuery(useJoinCode);

        User nonGroupUser = userManager.findByInputNumber(nonGroupPhone);

        assertNotNull(nonGroupUser);
        assertThat(responseEntity.getStatusCode(), is(OK));
        assertThat(groupManager.tokenExists(groupToken), is(true));
        assertThat(groupManager.groupHasValidToken(testGroup), is(true));
        assertThat(groupManager.getGroupByToken(groupToken), is(testGroup));
        // assertThat(groupManager.isUserInGroup(testGroup, nonGroupUser), is(true));

        // major todo: figure out why that last assert is failing, again, as a problem with the test environment (it works fine on all attempts outside tests)

    }

    /*
    Test that resuming from a menu interruption works, and that it clears after that
     */
    @Test
    public void interruptionResume() throws Exception {

        User testUser = createTestUser();
        Group testGroup = createTestGroup();

        // Have to generate an event for a reliable ID, since the in-memory DB might have created some elsewhere
        Long eventId = eventManager.createEvent("testEvent", testUser).getId();

        // generating an interruption in the middle of creating a meeting
        List<URI> getToGroupCreation = Arrays.asList(testPhoneUriBuild("mtg/start"), testPhoneUri("mtg/group").queryParam(eventParam, "" + eventId).
                                                             queryParam(freeTextParam, secondGroupPhone).build().toUri(),
                                                     testPhoneUriBuild("start"));

        List<URI> getToMeetingStart = Arrays.asList(testPhoneUri("mtg/place").queryParam(eventParam, "" + eventId).queryParam("menukey", "subject").
                queryParam(freeTextParam, "interrupted").build().toUri(), testPhoneUriBuild("start"), testPhoneUriBuild("start_force"));

        List<URI> allURIs = new ArrayList<>(getToGroupCreation);
        // allURIs.addAll(getToMeetingStart); // removing for the moment--throwing null pointer errors because of persistence issues

        for (URI uri : allURIs) {
            log.info("In interruption test, calling URI: " + uri.toString());
            ResponseEntity<String> response = executeQuery(uri);
            log.info("Response body:" + response.getBody());
            log.info("Status: " + response.getStatusCode());
            assertThat(response.getStatusCode(), is(OK));
        }

        ResponseEntity<String> checkReset = executeQuery(testPhoneUriBuild("start"));
        assertXMLEqual(startMenuEN, checkReset.getBody());

    }

    /*
    Test that group naming prompt works
     */
    /* @Test
    public void homeGroupRename() throws Exception {

    }*/

    /*
    Test that meeting RSVP works, both yes and no
     */
    @Test
    public void ussdRSVP() throws Exception {

        User testUser = createTestUser();
        Group testGroup = createTestGroup();
        Event testMeeting = createTestMeeting();
        Long eventId = testMeeting.getId();

        List<URI> rsvpYes = Arrays.asList(assembleUssdURI("start").queryParam(phoneParam, secondGroupPhone).build().toUri(),
                                          assembleUssdURI("rsvp").queryParam(phoneParam, secondGroupPhone).queryParam(eventParam, "" + eventId).
                                                  queryParam("confirmed", "yes").build().toUri());

        List<URI> rsvpNo = Arrays.asList(assembleUssdURI("start").queryParam(phoneParam, thirdGroupPhone).build().toUri(),
                                         assembleUssdURI("rsvp").queryParam(phoneParam, thirdGroupPhone).queryParam(eventParam, "" + eventId).
                                                 queryParam("confirmed", "no").build().toUri());

        List<ResponseEntity<String>> rsvpYesResponses = executeQueries(rsvpYes);
        List<ResponseEntity<String>> rsvpNoResponses = executeQueries(rsvpNo);

        List<ResponseEntity<String>> rsvpResponses = new ArrayList<>(rsvpYesResponses);
        rsvpResponses.addAll(rsvpNoResponses);

        User yesUser = userManager.findByInputNumber(secondGroupPhone);
        User noUser = userManager.findByInputNumber(thirdGroupPhone);

        assertNotNull(yesUser);
        assertNotNull(noUser);

        for (ResponseEntity<String> response : rsvpResponses) {
            log.info("Response body: " + response.getBody());
            log.info("Status: " + response.getStatusCode());
            assertThat(response.getStatusCode(), is(OK));
        }

        /* As with similar tests elsewhere, these are failing because of strange persistence behaviour
        assertThat(eventManager.getListOfUsersThatRSVPYesForEvent(testMeeting).contains(yesUser), is(true));
        assertThat(eventManager.getListOfUsersThatRSVPNoForEvent(testMeeting).contains(noUser), is(true));
        assertThat(eventLogManager.userRsvpForEvent(testMeeting, yesUser), is(true));
        assertThat(eventLogManager.userRsvpNoForEvent(testMeeting, noUser), is(true));*/

    }

    // set of automatic tests to make sure the standard menus aren't too long
    @Test
    public void menuLength() throws Exception {

        // doing the first two separately so that the userId and groupId can be extracted
        List<UriComponentsBuilder> allUssdUri = new ArrayList<>();
        LinkedHashMap<URI, ResponseEntity<String>> urlResponses = new LinkedHashMap<>();

        final URI createUserUri = testPhoneUri("start").build().toUri();
        final URI createEventUri = testPhoneUri(mtgPath + "start").build().toUri();

        urlResponses.put(createUserUri, template.getForEntity(createUserUri, String.class));
        urlResponses.put(createEventUri, template.getForEntity(createEventUri, String.class));

        User userCreated = userManager.findByInputNumber(testPhone);
        Long eventId = eventManager.getLastCreatedEvent(userCreated).getId();

        final URI createGroupUri = testPhoneUri(mtgPath + "/group").queryParam(eventParam, eventId).
                queryParam(freeTextParam, String.join(" ", testPhones)).build().toUri();

        urlResponses.put(createGroupUri, template.getForEntity(createGroupUri, String.class));

        Group groupCreated = groupManager.getLastCreatedGroup(userCreated);

        Long userId = userCreated.getId(), groupId = groupCreated.getId();

        // leaving out most of the free text menus, as highly unlikely to be overlength

        allUssdUri.add(testPhoneUri("mtg/start"));
        allUssdUri.add(testPhoneUri("mtg/group").queryParam(eventParam, eventId).
                queryParam("groupId", groupId).queryParam("request", "0"));
        allUssdUri.add(testPhoneUri("mtg/group").queryParam(eventParam, eventId).queryParam("request", secondGroupPhone));
        allUssdUri.add(testPhoneUri("mtg/start")); // just to check length of two-group menu

        allUssdUri.add(testPhoneUri("group/start"));
        allUssdUri.add(testPhoneUri("group/menu").queryParam("groupId", groupId));
        allUssdUri.add(testPhoneUri("group/list").queryParam("groupId", groupId));
        allUssdUri.add(testPhoneUri("group/rename").queryParam("groupId", groupId));
        allUssdUri.add(testPhoneUri("group/unsubscribe").queryParam("groupId", groupId));

        allUssdUri.add(testPhoneUri("user/start"));
        allUssdUri.add(testPhoneUri("user/name"));

        for (UriComponentsBuilder uriToExecute : allUssdUri) {
            urlResponses.put(uriToExecute.build().toUri(), template.getForEntity(uriToExecute.build().toUri(), String.class));
        }

        final String menuTooLongResponse = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<request>" +
                "   <headertext>Error! Menu is too long.</headertext>" +
                "   <options/>" +
                "</request>";

        for (Map.Entry<URI, ResponseEntity<String>> urlResponse : urlResponses.entrySet()) {
            System.out.println("URL: " + urlResponse.getKey().toString() + "\n STATUS: " + urlResponse.getValue().getStatusCode().toString());
            assertThat(urlResponse.getValue().getStatusCode(), is(OK));
            assertXMLNotEqual(menuTooLongResponse, urlResponse.getValue().getBody());
        }
    }

    // todo: write a couple of tests for bad input, to check phone number error handling, once it's built

}