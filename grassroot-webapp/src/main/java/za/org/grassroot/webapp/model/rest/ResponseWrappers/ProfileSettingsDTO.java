package za.org.grassroot.webapp.model.rest.ResponseWrappers;

/**
 * Created by paballo on 2016/04/19.
 */

public class ProfileSettingsDTO {

    private String displayName;
    private String language;
    private String alertPreference;

    public ProfileSettingsDTO(String displayName, String language, String alertPreference){
        this.displayName =displayName;
        this.language = language;
        this.alertPreference =alertPreference;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLanguage() {
        return language;
    }

    public String getAlertPreference() {
        return alertPreference;
    }
}
