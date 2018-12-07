package za.org.grassroot.core.dto;

import lombok.Value;
import za.org.grassroot.core.enums.Province;

import java.util.Locale;

@Value
public class UserMinimalProjection {

    String uid, displayName, languageCode;
    Province province;

    public Locale getLocale() {
        return (getLanguageCode() == null || getLanguageCode().trim().isEmpty()) ? Locale.ENGLISH : new Locale(getLanguageCode());
    }

    public boolean hasName() {
        return (displayName != null && displayName.trim().length() > 0);
    }

}
