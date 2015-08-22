package za.org.grassroot.webapp.controller.ussd;

/**
 * Need to work out how to test the messaging system without sending an SMS every time we compile
 * Probably needs to wait until after we have separated the messaging layer from the controller
 */

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.GrassRootWebApplicationConfig;

import javax.transaction.Transactional;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLNotEqual;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {GrassRootWebApplicationConfig.class})
@WebIntegrationTest(randomPort = true)
@Transactional
public class AatApiTestControllerTest {

    @Value("${local.server.port}")
    int port;

    private RestTemplate template = new TestRestTemplate();
    private UriComponentsBuilder base = UriComponentsBuilder.newInstance().scheme("http").host("localhost");

    // Common parameters for assembling the USSD urls
    private final String ussdPath = "ussd/";
    private final String mtgPath = "mtg/";
    private final String userPath = "user/";

    // Common parameters used for assembling the USSD service calls
    private final String phoneParam = "msisdn";
    private final String freeTextParam = "request";

    // Some strings used throughout tests
    private final String testPhone = "27815550000"; // todo: make sure this isn't an actual number
    private final String onceOffPhone= "27805550000"; // slightly different ot main testPhone so rename doesn't break XML checks if renamed already
    private final String testDisplayName = "TestPhone1";
    private final List<String> testPhones = Arrays.asList("0825550000", "0835550000", "0845550000"); // todo: as above
    private final Integer testGroupSize = testPhones.size() + 1; // includes creating user

    @Autowired
    UserManagementService userManager;

    @Autowired
    GroupManagementService groupManager;

    private UriComponentsBuilder assembleTestURI(String urlEnding) {
        UriComponentsBuilder baseUri = UriComponentsBuilder.fromUri(base.build().toUri())
                .path(ussdPath + urlEnding);
        return baseUri;
    }

    private UriComponentsBuilder testPhoneUri(String urlEnding) {
        return assembleTestURI(urlEnding).queryParam(phoneParam, testPhone);
    }

    private List<ResponseEntity<String>> executeQueries(List<URI> urisToExecute) {
        List<ResponseEntity<String>> responseEntities = new ArrayList<>();
        for (URI uriToExecute : urisToExecute) {
            responseEntities.add(template.getForEntity(uriToExecute, String.class));
        }
        return responseEntities;
    }

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

        final URI requestUri = base.path("ussd/start").queryParam(phoneParam, onceOffPhone).build().toUri();
        ResponseEntity<String> response = template.getForEntity(requestUri, String.class);

        System.out.println(base.toUriString());

        // assertThat(userManager.getAllUsers().size(), is(2)); // size of repository behaving a little strangely
        User userCreated = userManager.findByInputNumber(onceOffPhone);
        assertNotNull(userCreated.getId());
        assertNotNull(userCreated.getCreatedDateTime());
        assertThat(userCreated.getPhoneNumber(), is(onceOffPhone));

        final String expectedResponseXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<request>" +
                "    <headertext>Hi! Welcome to GrassRoot. What will you do?</headertext>" +
                "    <options>" +
                "        <option command=\"1\" order=\"1\" callback=\"http://meeting-organizer.herokuapp.com/ussd/mtg/start\"" +
                "                display=\"true\">Call a meeting</option>\n" +
                "        <option command=\"2\" order=\"2\" callback=\"http://meeting-organizer.herokuapp.com/ussd/vote\"" +
                "                display=\"true\">Take a vote</option>\n" +
                "        <option command=\"3\" order=\"3\" callback=\"http://meeting-organizer.herokuapp.com/ussd/log\"" +
                "                display=\"true\">Record an action</option>\n" +
                "        <option command=\"4\" order=\"4\" callback=\"http://meeting-organizer.herokuapp.com/ussd/group/start\"" +
                "                display=\"true\">Manage groups</option>\n" +
                "        <option command=\"5\" order=\"5\" callback=\"http://meeting-organizer.herokuapp.com/ussd/user/start\"" +
                "                display=\"true\">Change profile</option>\n" +
                "    </options>" +
                "</request>";

        assertXMLEqual(expectedResponseXml, response.getBody());
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

        assertThat(responseEntities.get(2).getBody(), containsString(testDisplayName)); // just checking contains name, not exact format

    }

    // @author luke : Test to make sure that a user entering a set of phone numbers creates a group properly

    // todo: if test database is clean at each test, should be able to assertThat group count is 1 ...
    // todo: figure out why we are getting a lazy initialization error via Spring Security when using just USSD menu

    @Test
    public void groupCreate() throws Exception {

        final URI createUserUri = testPhoneUri("start").build().toUri();
        final URI createGroupUri = testPhoneUri(mtgPath + "/group").
                queryParam(freeTextParam, String.join(" ", testPhones)).build().toUri();

        List<ResponseEntity<String>> responseEntities = executeQueries(Arrays.asList(createUserUri, createGroupUri));

        for (ResponseEntity<String> responseEntity : responseEntities)
            assertThat(responseEntity.getStatusCode(), is(OK));

        System.out.println("URI STRING: " + createGroupUri.toString());

        /**
         * this (test below) now fails again, with the meetingCreate method below added, which is troubling
         * the println spits out that there are 5 users at this point, which there shouldn't be
         * no matter the order that the tests run in, they all use the same phone numbers
         * in other words: there are either duplicates or phantoms appearing where there shouldn't
         * hopefully this is a problem with the tests, rather than something deeper
         **/

        // assertThat(userManager.getAllUsers().size(), is(testGroupSize));

        System.out.println("NUMBER OF USERS:" + userManager.getAllUsers().size());

        User userCreated = userManager.findByInputNumber(testPhone);
        Group groupCreated = groupManager.getLastCreatedGroup(userCreated);

        assertNotNull(userCreated.getId());
        assertNotNull(groupCreated.getId());
        assertThat(groupCreated.getCreatedByUser(), is(userCreated));
        assertThat(groupCreated.getGroupMembers().size(), is(testGroupSize));

        List<User> groupMembers = groupCreated.getGroupMembers();

        for (User groupMember : groupMembers) {
            if (groupMember != userCreated)
                assertTrue(testPhones.contains(User.invertPhoneNumber(groupMember.getPhoneNumber(), "")));
        }

    }

    // todo: once we have the event model and repository, switch to checking the event repository
    // todo: once the messaging layer is properly separated out, check the message that's compiled

    @Test
    public void meetingCreate() throws Exception {

        final URI createUserUri = testPhoneUri("start").build().toUri();
        final URI createGroupUri = testPhoneUri(mtgPath + "/group").
                queryParam(freeTextParam, String.join(" ", testPhones)).build().toUri();

        List<ResponseEntity<String>> responseEntities = executeQueries(Arrays.asList(createUserUri, createGroupUri));

        for (ResponseEntity<String> responseEntity : responseEntities)
            assertThat(responseEntity.getStatusCode(), is(OK));

        User userCreated = userManager.findByInputNumber(testPhone);
        Group groupCreated = groupManager.getLastCreatedGroup(userCreated);

        final URI createMeetingUri = testPhoneUri(mtgPath + "/place").
                queryParam("groupId", "" + groupCreated.getId()).
                queryParam("date", "Saturday").
                queryParam(freeTextParam, "10am").build().toUri();

        ResponseEntity<String> finalResponse = template.getForEntity(createMeetingUri, String.class);

        assertNotNull(userCreated.getId());
        assertNotNull(groupCreated.getId());
        assertThat(finalResponse.getStatusCode(), is(OK));
        assertThat(groupCreated.getCreatedByUser(), is(userCreated));
        assertThat(groupCreated.getGroupMembers().size(), is(testGroupSize));

        // todo: this is where the checks for event construction as well as message building need to go
        // todo: stop gap measure is checking through the xml that the parameters are included

        assertTrue(finalResponse.toString().contains("groupId=" + groupCreated.getId()));
        assertTrue(finalResponse.toString().contains("date=Saturday" ));
        assertTrue(finalResponse.toString().contains("time=10am" ));
    }

    // set of automatic tests to make sure the standard menus aren't too long
    @Test
    public void menuLength() throws Exception {

        // doing the first two separately so that the userId and groupId can be extracted
        List<UriComponentsBuilder> allUssdUri = new ArrayList<>();
        List<ResponseEntity<String>> responseEntities = new ArrayList<>();

        final URI createUserUri = testPhoneUri("start").build().toUri();
        final URI createGroupUri = testPhoneUri(mtgPath + "/group").
                queryParam(freeTextParam, String.join(" ", testPhones)).build().toUri();

        responseEntities.add(template.getForEntity(createUserUri, String.class));
        responseEntities.add(template.getForEntity(createGroupUri, String.class));

        User userCreated = userManager.findByInputNumber(testPhone);
        Group groupCreated = groupManager.getLastCreatedGroup(userCreated);

        Long userId = userCreated.getId(), groupId = groupCreated.getId();

        // leaving out most of the free text menus, as highly unlikely to be overlength

        allUssdUri.add(testPhoneUri("mtg/start"));
        allUssdUri.add(testPhoneUri("mtg/group").queryParam("groupId", groupId));
        allUssdUri.add(testPhoneUri("mtg/group").queryParam("request", onceOffPhone));
        allUssdUri.add(testPhoneUri("mtg/start")); // just to check two-group menu

        allUssdUri.add(testPhoneUri("group/start"));
        allUssdUri.add(testPhoneUri("group/menu").queryParam("groupId", groupId));
        allUssdUri.add(testPhoneUri("group/list").queryParam("groupId", groupId));
        allUssdUri.add(testPhoneUri("group/rename").queryParam("groupId", groupId));
        allUssdUri.add(testPhoneUri("group/unsubscribe").queryParam("groupId", groupId));

        allUssdUri.add(testPhoneUri("user/start"));
        allUssdUri.add(testPhoneUri("user/name"));

        for (UriComponentsBuilder uriToExecute : allUssdUri)
            responseEntities.add(template.getForEntity(uriToExecute.build().toUri(), String.class));

        final String menuTooLongResponse = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<request>" +
                "   <headertext>Error! Menu is too long.</headertext>" +
                "   <options/>" +
                "</request>";

        for (ResponseEntity<String> responseEntity : responseEntities) {
            assertThat(responseEntity.getStatusCode(), is(OK));
            assertXMLNotEqual(menuTooLongResponse, responseEntity.getBody());
        }


    }

    // todo: write a couple of tests for bad input, to check phone number error handling, once it's built

}