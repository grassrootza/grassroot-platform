package za.org.grassroot.webapp.controller.ussd.menus;

import java.util.*;

/**
 * @author luke on 2015/08/17.
 * Note: Currently assuming that whatever uses this will be well-behaved in passing a linked hash map, i.e., with the
 * menu options in the right order. Need to prominently document that, or add methods to sort/deal with unsorted.
 *
 * todo: figure out how to combine localization in here with insertion of context-dependent strings, such as user
 * names. possibly use a list of strings, and check for each if a localization key, if not, insert? possibly
 *
 * Note: Depending on solution to above, if we use a common key for URL endings and keys in localization files, can simplify constructor
 */
public class USSDMenu {

    protected static final String BASE_URI_STRING = "http://meeting-organizer.herokuapp.com/ussd/";

    protected String promptMessage;
    protected boolean isFreeText;

    protected LinkedHashMap<String, String> menuOptions; // key is URL and value is description text

    // most common and basic constructor, initialized options string and defaults to a menu (not free text)
    public USSDMenu(String promptMessage) {
        this.promptMessage = promptMessage;
        this.isFreeText = false;
        this.menuOptions = new LinkedHashMap<>();
    }
    
    // constructor for free-text menu, i.e., if pass a string, assume free text, if a map, assume menu
    public USSDMenu(String promptMessage, String nextUri) {
        this(promptMessage);
        this.isFreeText = true;
        this.menuOptions.put(nextUri, "");
    }

    // constructor for multi-option menu, where keys and string already known
    public USSDMenu(String promptMessage, Map<String, String> nextOptionKeys) {
        this(promptMessage);
        this.menuOptions = new LinkedHashMap<>(nextOptionKeys);
    }

    // now starting getters and setters

    public String getPromptMessage() {
        // todo : introduce localization here later (or in controller?), for now just returning the prompt
        return promptMessage;
    }

    public void setPromptMessage(String promptMessage) {
        // todo: as above, make this use localization rather than English
        this.promptMessage = promptMessage;
    }

    public boolean isFreeText() {
        return isFreeText;
    }

    public void setFreeText(boolean isFreeText) { this.isFreeText = isFreeText; }

    public void setNextURI(String nextUri) {
        // todo: throw an exception if this is not a free text
        menuOptions.put(nextUri, "");
    }

    public String getNextURI() {
        // todo: throw an exception if this is not a free text
        return menuOptions.entrySet().iterator().next().getKey();
    }

    public LinkedHashMap<String, String> getMenuOptions() {
        return menuOptions;
    }

    public void setMenuOptions(LinkedHashMap<String, String> menuOptions) {
        this.menuOptions = menuOptions;
    }

    public void addMenuOption(String optionURL, String optionDescription) {
        this.menuOptions.put(optionURL, optionDescription);
    }

    public Integer getMenuCharLength(Integer enumLength) {
        Integer characterCount = 0;
        characterCount += promptMessage.length();

        if (!isFreeText) {
            for (Map.Entry<String, String> menuOption : menuOptions.entrySet())
                characterCount += enumLength + menuOption.getValue().length();
        }

        return characterCount;
    }

    public Integer getMenuCharLength() {
        return getMenuCharLength("1. ".length());
    }

}