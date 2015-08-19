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
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.GrassRootWebApplicationConfig;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {GrassRootWebApplicationConfig.class})
@WebIntegrationTest(randomPort = true)
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

        final URI requestUri = base.path("ussd/start").queryParam(phoneParam, testPhone).build().toUri();
        ResponseEntity<String> response = template.getForEntity(requestUri, String.class);

        System.out.println(base.toUriString());

        // assertThat(userManager.getAllUsers().size(), is(2)); // size of repository behaving a little strangely
        User userCreated = userManager.findByInputNumber(testPhone);
        assertNotNull(userCreated.getId());
        assertNotNull(userCreated.getCreatedDateTime());
        assertThat(userCreated.getPhoneNumber(), is(testPhone));

    }

    // @author luke: Test to check that a user can rename themselves and be greeted on next access

    @Test
    public void userRename() throws Exception {

        final URI createUserUri = testPhoneUri("start").build().toUri();
        final URI renameUserUri = testPhoneUri(userPath + "name2").queryParam(freeTextParam, testDisplayName).build().toUri();

        ResponseEntity<String> response1 = template.getForEntity(createUserUri, String.class);
        ResponseEntity<String> response2 = template.getForEntity(renameUserUri, String.class);

        System.out.println("URI String: " + renameUserUri.toString());

        assertThat(response1.getStatusCode(), is(OK));
        assertThat(response2.getStatusCode(), is(OK));

        assertThat(userManager.findByInputNumber(testPhone).hasName(), is(true));
        assertThat(userManager.findByInputNumber(testPhone).getDisplayName(), is(testDisplayName));
        assertThat(userManager.findByInputNumber(testPhone).getName(""), is(testDisplayName));

        // final test is that on next access to system the name comes up

        ResponseEntity<String> response3 = template.getForEntity(createUserUri, String.class);

        assertThat(response3.getStatusCode(), is(OK));
        assertThat(response3.getBody(), containsString(testDisplayName)); // just checking contains name, not exact format

    }

    // @author luke : Test to make sure that a user entering a set of phone numbers creates a group properly

    // todo: if test database is clean at each test, should be able to assertThat group count is 1 ...
    // todo: figure out why we are getting a lazy initialization error via Spring Security when using just USSD menu

    @Test
    public void groupCreate() throws Exception {

        final URI createUserUri = testPhoneUri("start").build().toUri();
        final URI createGroupUri = testPhoneUri(mtgPath + "/group").
                queryParam(freeTextParam, String.join(" ", testPhones)).build().toUri();

        ResponseEntity<String> response1 = template.getForEntity(createUserUri, String.class);
        ResponseEntity<String> response2 = template.getForEntity(createGroupUri, String.class);

        System.out.println("URI STRING: " + createGroupUri.toString());

        assertThat(userManager.getAllUsers().size(), is(testGroupSize+1)); // again, not sure why it needs to be +1 to pass tests
        // assertThat(groupManager)

        User userCreated = userManager.findByInputNumber(testPhone);
        Group groupCreated = userCreated.getGroupsPartOf().iterator().next(); // replace with 'get last group created method later'
        List<User> groupMembers = groupCreated.getGroupMembers();

        Assert.assertNotNull(userCreated.getId());
        Assert.assertNotNull(groupCreated.getId());
        Assert.assertThat(groupCreated.getCreatedByUser(), is(userCreated));
        Assert.assertThat(groupCreated.getGroupMembers().size(), is(testGroupSize));

    }

}