package za.org.grassroot.meeting_organizer.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.meeting_organizer.model.AAT.Option;
import za.org.grassroot.meeting_organizer.model.AAT.Request;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Controller to play around with the AAT api
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class AatApiTestController {

    @RequestMapping(value = "/question1")
    @ResponseBody
    public Request question1() throws URISyntaxException {
        final Option option = new Option("Yes I can!", 1,1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"),true);
        return new Request("Can you answer the question?", Collections.singletonList(option));
    }

}
