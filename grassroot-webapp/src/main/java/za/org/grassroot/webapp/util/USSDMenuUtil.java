package za.org.grassroot.webapp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2015/12/04.
 */
public class USSDMenuUtil {

    private static final Logger log = LoggerFactory.getLogger(USSDMenuUtil.class);

    private static final String baseURI = System.getenv("APP_URL") + "/ussd/";
    private static final int enumLength = ("1. ").length();

    public static List<Option> createMenu(Map<String, String> menuOptions) throws URISyntaxException {
        List<Option> menuToBuild = new ArrayList<>();
        Integer counter = 1;
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

    public static Request menuBuilder(USSDMenu thisMenu) throws URISyntaxException {
        Request menuRequest;
        if (thisMenu.isFreeText()) {
            menuRequest = new Request(thisMenu.getPromptMessage(), freeText(thisMenu.getNextURI()));
        } else if (checkMenuLength(thisMenu, false)) {
            menuRequest = new Request(thisMenu.getPromptMessage(), createMenu(thisMenu.getMenuOptions()));
        } else {// note: this runs the risk of cutting off crucial end-of-prompt info, but is 'least bad' option so far (other is just an error message)
            Integer charsToTrim = thisMenu.getMenuCharLength() - 159; // adding a character, for safety
            String currentPrompt = thisMenu.getPromptMessage();
            String revisedPrompt = currentPrompt.substring(0, currentPrompt.length() - charsToTrim);
            menuRequest = new Request(revisedPrompt, createMenu(thisMenu.getMenuOptions()));
        }
        return menuRequest;
    }
    //todo: remove code smell
    public static Request menuBuilder(USSDMenu thisMenu, boolean isFirstMenu) throws URISyntaxException {
        Request menuRequest;
        if (thisMenu.isFreeText()) {
            menuRequest = new Request(thisMenu.getPromptMessage(), freeText(thisMenu.getNextURI()));
        } else if (checkMenuLength(thisMenu, isFirstMenu) ){
            menuRequest = new Request(thisMenu.getPromptMessage(), createMenu(thisMenu.getMenuOptions()));
        } else {
            Integer charsToTrim = thisMenu.getMenuCharLength() - 139; // adding a character, for safety
            String currentPrompt = thisMenu.getPromptMessage();
            String revisedPrompt = currentPrompt.substring(0, currentPrompt.length() - charsToTrim);
            menuRequest = new Request(revisedPrompt, createMenu(thisMenu.getMenuOptions()));
        }
        return menuRequest;
    }



    public static List<Option> freeText(String urlEnding) throws URISyntaxException {
        return Collections.singletonList(new Option("", 1, 1, new URI(baseURI + urlEnding), false));
    }

    public static boolean checkMenuLength(USSDMenu menuToCheck, boolean firstMenu) {

        Integer characterLimit = firstMenu ? 140 : 160;

        log.info("Menu with prompt message ... " + menuToCheck.getPromptMessage());
        log.info("Length of menu: " + menuToCheck.getMenuCharLength(enumLength));

        return (menuToCheck.getMenuCharLength(enumLength) < characterLimit); // might be able to get away with <=, but prefer to be conservative
    }


}
