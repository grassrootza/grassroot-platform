package za.org.grassroot.core.util;

import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// for broadcast content, etc., that allows swapping out of entity names for another
public interface GrassrootTemplate {

    String NAME_FIELD_TEMPLATE = "{__name__}";
    String CONTACT_FIELD_TEMPALTE = "{__contact__}";
    String DATE_FIELD_TEMPLATE = "{__date__}";
    String PROVINCE_FIELD_TEMPLATE = "{__province__}";
    String INBOUND_FIELD_TEMPLATE = "{__inbound__}";
    String ENTITY_FIELD_TEMPLATE = "{__entity_name__}";

    DateTimeFormatter SDF = DateTimeFormatter.ofPattern("EEE d MMM");
    String NO_PROVINCE = "your province";

    default String mergeTemplate(User destination, String template) {

        final String formatString = template
                .replace(NAME_FIELD_TEMPLATE, "%1$s")
                .replace(CONTACT_FIELD_TEMPALTE, "%2$s")
                .replace(DATE_FIELD_TEMPLATE, "%3$s")
                .replace(PROVINCE_FIELD_TEMPLATE, "%4$s");

        final String nameToUse = StringUtils.isEmpty(destination.getDisplayName()) ? "friend" : destination.getName();
        return String.format(formatString,
                nameToUse,
                destination.getUsername(),
                SDF.format(LocalDateTime.now()),
                destination.getProvince() == null ?
                        NO_PROVINCE :
                        Province.CANONICAL_NAMES_ZA.getOrDefault(destination.getProvince(), NO_PROVINCE))
                .trim().replaceAll(" +", " ");
    }

}
