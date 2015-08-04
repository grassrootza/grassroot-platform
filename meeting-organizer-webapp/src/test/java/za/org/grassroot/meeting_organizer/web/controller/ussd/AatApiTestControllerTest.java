package za.org.grassroot.meeting_organizer.web.controller.ussd;

import java.net.URI;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.meeting_organizer.Application;
import za.org.grassroot.meeting_organizer.common.GrassRootMeetingOrganiserProfiles;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Application.class})
@WebIntegrationTest(randomPort = true)
@Profile(GrassRootMeetingOrganiserProfiles.TEST)
public class AatApiTestControllerTest {

    @Value("${local.server.port}")
    int port;

    private RestTemplate template = new TestRestTemplate();
    private UriComponentsBuilder base = UriComponentsBuilder.newInstance().scheme("http").host("localhost");

    @Before
    public void setUp() throws Exception {
        base.port(port);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalizeWhitespace(true);
    }

    @Test
    public void getHello() throws Exception {
        final URI requesUri = base.path("ussd/test_question").build().toUri();
        ResponseEntity<String> response = template.getForEntity(requesUri, String.class);

        assertThat(response.getStatusCode(),is(OK));
        final String expectedResponseXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                                           "<request>" +
                                           "    <headertext>Can you answer the question?</headertext>" +
                                           "    <options>" +
                                           "        <option command=\"1\" order=\"1\" callback=\"http://yourdomain.tld/ussdxml.ashx?file=2\"" +
                                           "                display=\"true\">Yes I can!</option>\n" +
                                           "    </options>" +
                                           "</request>";
        System.out.println(response.getBody());
        // Test seem to be failing: Empty xml returned
               assertXMLEqual(expectedResponseXml, response.getBody());
    }

}