package za.org.grassroot.webapp.controller.ussd.menus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author luke on 2015/08/17.
 * Note: Currently assuming that whatever uses this will be well-behaved in passing a linked hash map, i.e., with the
 * menu options in the right order. Need to prominently document that, or add methods to sort/deal with unsorted.
 *
 */
public class USSDMenu {

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

    // constructor for empty shell, useful in various menus
    public USSDMenu() {
        this.promptMessage = "";
        this.isFreeText = false;
        this.menuOptions = new LinkedHashMap<>();
    }

    // constructor for empty shell for free text
    public USSDMenu(boolean isFreeText) {
        this.promptMessage = "";
        this.isFreeText = isFreeText;
        this.menuOptions = new LinkedHashMap<>();
    }

    // now starting getters and setters

    public String getPromptMessage() {
        return promptMessage;
    }

    public void setPromptMessage(String promptMessage) {
        this.promptMessage = promptMessage;
    }

    public boolean isFreeText() {
        return isFreeText;
    }

    public void setFreeText(boolean isFreeText) { this.isFreeText = isFreeText; }

    public void setNextURI(String nextUri) {
        this.isFreeText = true;
        menuOptions.put(nextUri, "");
    }

    public String getNextURI() {
        return menuOptions.entrySet().iterator().next().getKey();
    }

    public LinkedHashMap<String, String> getMenuOptions() {
        return menuOptions;
    }

    public boolean hasOptions() {
        return menuOptions != null && !menuOptions.isEmpty();
    }

    public void setMenuOptions(LinkedHashMap<String, String> menuOptions) {
        this.menuOptions = menuOptions;
    }

    public void addMenuOption(String optionURL, String optionDescription) {
        this.menuOptions.put(optionURL, optionDescription);
    }

    public void addMenuOptions(Map<String, String> menuOptions) {
        this.menuOptions.putAll(menuOptions);
    }

    public Integer getMenuCharLength(Integer enumLength) {
        Integer characterCount = 0;
        characterCount += (promptMessage == null) ? 0 : promptMessage.length();

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