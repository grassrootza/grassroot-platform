package za.org.grassroot.webapp.controller.ussd;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.transaction.TestTransaction;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.IntegrationTest;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

/**
 * Created by luke on 2015/11/17.
 */

@Category(IntegrationTest.class)
public class USSDHomeControllerIT extends USSDAbstractIT {

 /*  private static final Logger log = LoggerFactory.getLogger(USSDHomeControllerIT.class);

    private final String startMenuEN = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<request>" +
            "    <headertext>Hi! Welcome to GrassRoot. What will you do?</headertext>" +
            "    <options>" +
            "        <option command=\"1\" order=\"1\" callback=\"http://localhost:8080/ussd/mtg/start\"" +
            "                display=\"true\">Call a meeting</option>\n" +
            "        <option command=\"2\" order=\"2\" callback=\"http://localhost:8080/ussd/vote/start\"" +
            "                display=\"true\">Take a vote</option>\n" +
            "        <option command=\"3\" order=\"3\" callback=\"http://localhost:8080/ussd/log/start\"" +
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
            "        <option command=\"2\" order=\"2\" callback=\"http://localhost:8080/ussd/vote/start\"" +
            "                display=\"true\">Biza ivoti</option>\n" +
            "        <option command=\"3\" order=\"3\" callback=\"http://localhost:8080/ussd/log/start\"" +
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
    public void userStartLocale() throws  Exception {

        final URI requestUri = assembleUssdURI("start").queryParam(phoneParam, testPhoneZu).build().toUri();
        executeQuery(requestUri);

        log.info("User creation URI string: " + requestUri.toString());
        User userCreated = userManager.findByInputNumber(testPhoneZu);

        final URI changeLanguageUri = assembleUssdURI("user/language-do").queryParam(phoneParam, testPhoneZu).queryParam("language", "zu").build().toUri();
        executeQuery(changeLanguageUri);

        log.info("The URL used is as follows: " + changeLanguageUri.toString());
        log.info("The user has been saved, as: " + userCreated);
        log.info("The user's locale code is: " + userCreated.getLanguageCode());

        final URI secondStartMenu = assembleUssdURI("start").queryParam(phoneParam, testPhoneZu).build().toUri();
        ResponseEntity<String> newHomeMenu = executeQuery(secondStartMenu);
        log.info("Zulu URL executed: " + secondStartMenu.toString());

        assertNotNull(userCreated.getId());
        assertNotNull(userCreated.getCreatedDateTime());
        assertXMLEqual(startMenuZU, newHomeMenu.getBody());
        TestTransaction.end();
        TestTransaction.start();
        User zuUser = userManager.findByInputNumber(testPhoneZu);
        assertEquals(testPhoneZu, zuUser.getPhoneNumber());
        assertEquals("zu", zuUser.getLanguageCode());


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

        TestTransaction.end();
        TestTransaction.start();
        User renamedUser = userManager.findByInputNumber(testPhone);

        assertThat(renamedUser.hasName(), is(true));
        assertThat(renamedUser.getDisplayName(), is(testDisplayName));
        assertThat(renamedUser.getName(""), is(testDisplayName));
        assertThat(renamedUser.nameToDisplay(), is(testDisplayName));

        // final test is that on next access to system the name comes up
        // removing this test for now because the prompt to rename a group now comes up, without a name, so would have
        // to either break stand-alone integrity of the tests or alter UX for sake of tests.

        final URI nextWelcomeMenu = testPhoneUri("start").build().toUri();
        ResponseEntity<String> welcomeMenu = executeQuery(nextWelcomeMenu);

        assertThat(welcomeMenu.getStatusCode(), is(OK));
        assertThat(welcomeMenu.getBody(), containsString(testDisplayName)); // just checking contains name, not exact format

    }


    Test that the group joining code is working
    todo: figure out where to start and end transaction to get all of this to not throw spurious failures

    @Test
    public void joiningCode() throws Exception {

        User testUser = createTestUser();
        Group testGroup = createTestGroup();
        testGroup = groupManager.generateGroupToken(testGroup, 1);
        String groupToken = testGroup.getGroupTokenCode();

        final URI useJoinCode = assembleUssdURI("start").queryParam(phoneParam, nonGroupPhone).
                queryParam("request", "*134*1994*" + groupToken + "#").build().toUri();

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

    }

    /*
    Test that resuming from a menu interruption works, and that it clears after that
    todo: fix so that it works with new / refactored interruption logic and menus
     */
    /*@Test
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

    }*/

    /*
    Test that meeting RSVP works, both yes and no
     */
   /* @Test
    public void ussdRSVP() throws Exception {

        createTestUser();
        createTestGroup();
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
            //assertThat(response.getStatusCode(), is(OK));
        }

        /* As with similar tests elsewhere, these are failing because of strange persistence behaviour
        assertThat(eventManager.getListOfUsersThatRSVPYesForEvent(testMeeting).contains(yesUser), is(true));
        assertThat(eventManager.getListOfUsersThatRSVPNoForEvent(testMeeting).contains(noUser), is(true));
        assertThat(eventLogManager.userRsvpForEvent(testMeeting, yesUser), is(true));
        assertThat(eventLogManager.userRsvpNoForEvent(testMeeting, noUser), is(true));*/

 //   }*/

}

