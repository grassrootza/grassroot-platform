package za.org.grassroot.webapp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2015/12/04.
 */
@Component
public class USSDMenuUtil {

    @Autowired
    private Environment environment;

    private static final Logger log = LoggerFactory.getLogger(USSDMenuUtil.class);

    private String baseURI;
    private int maxOpeningMenuLength;
    private int maxMenuLength;

    private static final int enumLength = ("1. ").length();

    @PostConstruct
    private void init() {
        baseURI = environment.getRequiredProperty("grassroot.ussd.return.url", String.class);
        maxOpeningMenuLength = environment.getRequiredProperty("grassroot.ussd.menu.length.opening", Integer.class);
        maxMenuLength = environment.getRequiredProperty("grassroot.ussd.menu.length.standard", Integer.class);
        log.info("ussd menu util initialized, baseURI = {}, maxMenuOpen = {}, maxMenuLength = {}...", baseURI, maxOpeningMenuLength, maxMenuLength);
    }

    // todo : when we move to Spring Boot 1.4 use new constructor patterns to avoid this
    public void setForTests() {
        if (environment != null) {
            throw new UnsupportedOperationException("Error! Can only call this method via tests");
        }
        this.baseURI = "http://127.0.0.1/ussd";
        this.maxOpeningMenuLength = 140;
        this.maxMenuLength = 160;
    }

    private List<Option> createMenu(Map<String, String> menuOptions) throws URISyntaxException {
        final List<Option> menuToBuild = new ArrayList<>();
        int counter = 1;
        for (Map.Entry<String, String> option : menuOptions.entrySet()) {
            menuToBuild.add(new Option(option.getValue(), counter, counter, new URI(baseURI + option.getKey()), true));
            counter++;
        }
        return menuToBuild;
    }

    /*
    integrating check for menu length in here, to avoid writing it in every return
    defaulting to not first screen, can do an override in start (shouldn't cause speed issues, but watch)
    not bothering to check length on a free text menu, since the odds of those exceeding are very low (and then a UX issue...)
    */

    public Request menuBuilder(USSDMenu thisMenu, boolean isFirstMenu) throws URISyntaxException {
        final Request menuRequest;
        if (thisMenu.isFreeText()) {
            menuRequest = new Request(thisMenu.getPromptMessage(), freeText(thisMenu.getNextURI()));
        } else if (checkMenuLength(thisMenu, isFirstMenu) ){
            menuRequest = new Request(thisMenu.getPromptMessage(), createMenu(thisMenu.getMenuOptions()));
        } else {
            // note: this runs the risk of cutting off crucial end-of-prompt info, but is 'least bad' option so far (other is just an error message)
            final Integer charsToTrim = thisMenu.getMenuCharLength() - (isFirstMenu ? (maxOpeningMenuLength - 1) : (maxMenuLength - 1)); // adding a character, for safety
            String currentPrompt = thisMenu.getPromptMessage();
            log.info("about to trim this current prompt = {}, and going to trim this many characters: {}", currentPrompt, charsToTrim);
            String revisedPrompt = currentPrompt.substring(0, Math.max(1, currentPrompt.length() - charsToTrim));
            menuRequest = new Request(revisedPrompt, createMenu(thisMenu.getMenuOptions()));
        }
        return menuRequest;
    }

    private List<Option> freeText(String urlEnding) throws URISyntaxException {
        return Collections.singletonList(new Option("", 1, 1, new URI(baseURI + urlEnding), false));
    }

    private boolean checkMenuLength(USSDMenu menuToCheck, boolean firstMenu) {
        final int characterLimit = firstMenu ? maxOpeningMenuLength : maxMenuLength;
        log.info("Length of menu: " + menuToCheck.getMenuCharLength(enumLength));
        return (menuToCheck.getMenuCharLength(enumLength) < characterLimit); // might be able to get away with <=, but prefer to be conservative
    }


}
