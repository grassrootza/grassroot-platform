package za.org.grassroot.webapp.controller.rest.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskPreview {

    private String smsMessage;
    private String ussdPrompt;
    private List<String> ussdOptions = new ArrayList<>();

    public void addUssdMenu(USSDMenu ussdMenu) {
        this.ussdPrompt = ussdMenu.getPromptMessage();
        ussdMenu.getMenuOptions().forEach((url, option) -> ussdOptions.add(option));
    }

}
