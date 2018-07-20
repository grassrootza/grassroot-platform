package za.org.grassroot.webapp.controller.ussd;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import za.org.grassroot.core.domain.User;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.springframework.http.HttpStatus.OK;

//import static org.hamcrest.CoreMatchers.containsString;

/**
 * Created by luke on 2015/11/17.
 */

public class USSDHomeControllerIT extends USSDAbstractIT {

   private static final Logger log = LoggerFactory.getLogger(USSDHomeControllerIT.class);

    private final String startMenuEN = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<request>" +
            "    <headertext>Hi! Welcome to Grassroot. What will you do?</headertext>" +
            "    <options>" +
            "        <option command=\"1\" order=\"1\" callback=\"http://localhost:8080/ussd/mtg/start\"" +
            "                display=\"true\">Call a meeting</option>\n" +
            "        <option command=\"2\" order=\"2\" callback=\"http://localhost:8080/ussd/vote/start\"" +
            "                display=\"true\">Take a vote</option>\n" +
            "        <option command=\"3\" order=\"3\" callback=\"http://localhost:8080/ussd/todo/start\"" +
            "                display=\"true\">Record an action</option>\n" +
            "        <option command=\"4\" order=\"4\" callback=\"http://localhost:8080/ussd/testGroup/start\"" +
            "                display=\"true\">Manage groups</option>\n" +
            "        <option command=\"5\" order=\"5\" callback=\"http://localhost:8080/ussd/user/start\"" +
            "                display=\"true\">Change profile</option>\n" +
            "    </options>" +
            "</request>";

    private final String startMenuZU = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<request>" +
            "    <headertext>Yebo, siyakwamukela KuGrassroot. Uzokwenzani?</headertext>" +
            "    <options>" +
            "        <option command=\"1\" order=\"1\" callback=\"http://localhost:8080/ussd/mtg/start\"" +
            "                display=\"true\">Biza umhlangano</option>\n" +
            "        <option command=\"2\" order=\"2\" callback=\"http://localhost:8080/ussd/vote/start\"" +
            "                display=\"true\">Biza ivoti</option>\n" +
            "        <option command=\"3\" order=\"3\" callback=\"http://localhost:8080/ussd/todo/start\"" +
            "                display=\"true\">Bhala isenzo</option>\n" +
            "        <option command=\"4\" order=\"4\" callback=\"http://localhost:8080/ussd/testGroup/start\"" +
            "                display=\"true\">Phatha amaqembu</option>\n" +
            "        <option command=\"5\" order=\"5\" callback=\"http://localhost:8080/ussd/user/start\"" +
            "                display=\"true\">Shintsha iprofile</option>\n" +
            "    </options>" +
            "</request>";

    @Before
    public void setUp() {
        base.port(port);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalizeWhitespace(true);
    }

    @Test
    public void userStartLocale() {

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
//        assertXMLEqual(startMenuZU, newHomeMenu.getBody());
        User zuUser = userManager.findByInputNumber(testPhoneZu);
        assertEquals(testPhoneZu, zuUser.getPhoneNumber());
        assertEquals("zu", zuUser.getLanguageCode());


    }

    // Test to check that a user can rename themselves and be greeted on next access

    @Test
    public void userRename() {

        final URI createUserUri = testPhoneUri("start").build().toUri();
        final URI renameUserUri = testPhoneUri(userPath + "name-do").queryParam(freeTextParam, testDisplayName).build().toUri();

        List<ResponseEntity<String>> responseEntities = executeQueries(Arrays.asList(createUserUri, renameUserUri, createUserUri));

        System.out.println("URI String: " + renameUserUri.toString());

        for (ResponseEntity<String> responseEntity : responseEntities)
            assertThat(responseEntity.getStatusCode(), is(OK));


        User renamedUser = userManager.findByInputNumber(testPhone);

        assertThat(renamedUser.hasName(), is(true));
        assertThat(renamedUser.getDisplayName(), is(testDisplayName));
        assertThat(renamedUser.getName(""), is(testDisplayName));
        assertThat(renamedUser.nameToDisplay(), is(testDisplayName));

        // final test is that on next access to system the name comes up
        // removing this test for now because the prompt to rename a testGroup now comes up, without a name, so would have
        // to either break stand-alone integrity of the tests or alter UX for sake of tests.

        final URI nextWelcomeMenu = testPhoneUri("start").build().toUri();
        ResponseEntity<String> welcomeMenu = executeQuery(nextWelcomeMenu);

        assertThat(welcomeMenu.getStatusCode(), is(OK));
       // assertThat(welcomeMenu.getBody(), containsString(testDisplayName)); // just checking contains name, not exact format

    }

}

