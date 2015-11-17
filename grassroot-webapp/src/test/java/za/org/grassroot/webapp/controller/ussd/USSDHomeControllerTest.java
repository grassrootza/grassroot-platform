package za.org.grassroot.webapp.controller.ussd;

/**
 * Need to work out how to test the messaging system without sending an SMS every time we compile
 * Probably needs to wait until after we have separated the messaging layer from the controller
 */

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import javax.persistence.EntityManager;
import java.net.URI;
import java.util.*;

import static junit.framework.Assert.assertNotNull;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLNotEqual;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

public class USSDHomeControllerTest extends USSDAbstractTest {

    protected static final Logger log = LoggerFactory.getLogger(USSDHomeControllerTest.class);

    @Autowired
    EntityManager em;


    private final String startMenuEN = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<request>" +
            "    <headertext>Hi! Welcome to GrassRoot. What will you do?</headertext>" +
            "    <options>" +
            "        <option command=\"1\" order=\"1\" callback=\"http://localhost:8080/ussd/mtg/start\"" +
            "                display=\"true\">Call a meeting</option>\n" +
            "        <option command=\"2\" order=\"2\" callback=\"http://localhost:8080/ussd/vote/start\"" +
            "                display=\"true\">Take a vote</option>\n" +
            "        <option command=\"3\" order=\"3\" callback=\"http://localhost:8080/ussd/log\"" +
            "                display=\"true\">Record an action</option>\n" +
            "        <option command=\"4\" order=\"4\" callback=\"http://localhost:8080/ussd/group/start\"" +
            "                display=\"true\">Manage groups</option>\n" +
            "        <option command=\"5\" order=\"5\" callback=\"http://localhost:8080/ussd/user/start\"" +
            "                display=\"true\">Change profile</option>\n" +
            "    </options>" +
            "</request>";

    private final String initMenuEN = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<request>" +
            "    <headertext>Welcome to GrassRoot. Which language do you want to use?</headertext>" +
            "    <options>" +
            "        <option command=\"1\" order=\"1\" callback=\"http://localhost:8080/ussd/start_language?language=en\"" +
            "                display=\"true\">English</option>\n" +
            "        <option command=\"2\" order=\"2\" callback=\"http://localhost:8080/ussd/start_language?language=nso\"" +
            "                display=\"true\">Sepedi</option>\n" +
            "        <option command=\"3\" order=\"3\" callback=\"http://localhost:8080/ussd/start_language?language=st\"" +
            "                display=\"true\">Sesotho</option>\n" +
            "        <option command=\"4\" order=\"4\" callback=\"http://localhost:8080/ussd/start_language?language=ts\"" +
            "                display=\"true\">Tsonga</option>\n" +
            "        <option command=\"5\" order=\"5\" callback=\"http://localhost:8080/ussd/start_language?language=zu\"" +
            "                display=\"true\">Zulu</option>\n" +
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
                .queryParam("request", "*134*1994#").build().toUri();
        ResponseEntity<String> response1 = template.getForEntity(requestUri, String.class); // gets initiation menu
        ResponseEntity<String> response2 = template.getForEntity(requestUri, String.class); // gets standard welcome menu


        System.out.println(base.toUriString());

        User userCreated = userManager.findByInputNumber(nonGroupPhone);
        assertNotNull(userCreated.getId());
        assertNotNull(userCreated.getCreatedDateTime());
        assertThat(userCreated.getPhoneNumber(), is(nonGroupPhone));

        /* at present, these tests are passing, though there is a risk that if order of tests is scrambled and/or persistence
        problems get in the way of the initiation flag being set, they may fail -- if so, will comment out second line  */
        assertXMLEqual(response1.getBody(), initMenuEN);
        assertXMLEqual(response2.getBody(), startMenuEN);
    }

    /*
    Test that group naming prompt works
     */
    /* @Test
    public void homeGroupRename() throws Exception {

    }*/


    /*
     Set of automatic tests to make sure the standard menus aren't too long
     todo: convert these into a set of unit tests (one per menu)
      */
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