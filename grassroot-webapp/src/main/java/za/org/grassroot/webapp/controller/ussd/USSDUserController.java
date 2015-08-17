package za.org.grassroot.webapp.controller.ussd;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * @author luke on 2015/08/14.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDUserController extends USSDController {

    /**
     * Starting the user profile management flow here
     */

    @RequestMapping(value = "ussd/user")
    @ResponseBody
    public Request userProfile(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = new User();

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        String returnMessage = "What would you like to do?";

        final Option changeName = new Option("Change my display name", 1,1, new URI(baseURI + "user/name"),true);
        final Option changeLanguage = new Option("Change my language", 2,2, new URI(baseURI + "user/language"),true);
        final Option addNumber = new Option("Add phone number to my profile", 3,3, new URI(baseURI + "user/phone"), true);

        return new Request(returnMessage, Arrays.asList(changeName, changeLanguage, addNumber));
    }

    @RequestMapping(value = "ussd/user/name")
    @ResponseBody
    public Request userDisplayName(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = new User();

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        String promptPhrase;
        if (!sessionUser.hasName()) {
            promptPhrase = "You haven't set your name yet. What name do you want to use?";
        } else {
            promptPhrase = "Your name is currently set to '" + sessionUser.getDisplayName() + "'. What do you want to change it to?";
        }

        return new Request(promptPhrase, freeText("user/name2"));
    }

    @RequestMapping(value = "ussd/user/name2")
    @ResponseBody
    public Request userChangeName(@RequestParam(value="msisdn", required=true) String inputNumber,
                                  @RequestParam(value="request", required=true) String newName) throws URISyntaxException {

        // todo: add validation and processing of the name that is passed, as well as exception handling etc

        User sessionUser = new User();

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        sessionUser.setDisplayName(newName);
        sessionUser = userManager.save(sessionUser);

        return new Request("Success! Name changed to " + sessionUser.getDisplayName(), new ArrayList<Option>());
    }

}
