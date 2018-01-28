package za.org.grassroot.services.campaign;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Locale;

// a map would be cleaner but JS/Angular HTTP client is disastrous at handling maps, hence
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MessageLanguagePair {

    private Locale language;
    private String message;
}