package za.org.grassroot.webapp.controller.ussd.menus;

import java.net.URI;
import java.net.URISyntaxException;
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

    protected String mappedURI;
    protected String fullURI;
    protected String promptMessage;
    protected boolean isFreeText;

    protected LinkedHashMap<String, String> menuOptions; // format is key and then description text

    // most common and basic constructor
    public USSDMenu(String thisUri, String promptMessage) {
        this.mappedURI = thisUri;
        this.fullURI = BASE_URI_STRING + mappedURI;
        this.promptMessage = promptMessage;
    }
    
    // constructor for free-text menu
    public USSDMenu(String thisUri, String promptMessage, String nextUri) {
        this(thisUri, promptMessage);
        this.isFreeText = true;
        this.menuOptions = new LinkedHashMap<>();
        this.menuOptions.put(nextUri, "");
    }
    
    // constructor for multi-option menu, where menu not already known
    public USSDMenu(String thisUri, String promptMessage, boolean isFreeText) {
        this(thisUri, promptMessage);
        this.isFreeText = isFreeText;
        this.menuOptions = new LinkedHashMap<>();
    }

    // constructor for multi-option menu, where keys and string already known
    public USSDMenu(String thisUri, String promptMessage, Map<String, String> nextOptionKeys) {
        this(thisUri, promptMessage);
        this.isFreeText = false;
        this.promptMessage = promptMessage;
        this.menuOptions = new LinkedHashMap<>(nextOptionKeys);
    }

    // now starting getters and setters

    public String getPromptMessage() {
        // todo : introduce localization here later, for now just returning the prompt
        return promptMessage;
    }

    public void setPromptMessage(String promptMessage) {
        // todo: as above, make this use localization rather than English
        this.promptMessage = promptMessage;
    }

    public boolean isFreeText() {
        return isFreeText;
    }

    public String getUri() {
        return mappedURI;
    }

    public URI getFullURI() throws URISyntaxException { return new URI(fullURI); }

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