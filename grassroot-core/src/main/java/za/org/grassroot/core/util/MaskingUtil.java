package za.org.grassroot.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by luke on 2016/02/03.
 */
public class MaskingUtil {

    private static final Logger log = LoggerFactory.getLogger(MaskingUtil.class);

    private static final char maskingCharacter = '*';
    private static final int phoneNumberLength = "27810001111".length(); // todo: make country specific
    private static final double shareOfNameToMask = 2/3;

    public static char getMaskingCharacter() { return maskingCharacter; }

    public static String maskPhoneNumber(String phoneNumber) {
        // todo: think about randomizing digits too
        // todo: do this properly, with character counts, not this quick hack
        char[] chars = new char[phoneNumberLength - 6];
        Arrays.fill(chars, maskingCharacter);
        return phoneNumber.substring(0,2) + (new String(chars)) + phoneNumber.substring(8, phoneNumberLength);
    }

    public static String maskName(String name) {
        if (name == null || name.trim().equals("")) return name;

        int lengthOfName = name.length();
        int lengthToMask = (int) Math.ceil(lengthOfName * shareOfNameToMask);
        int lengthOfRemainder = (lengthOfName - lengthToMask) / 2;

        char[] mask = new char[lengthToMask];
        Arrays.fill(mask, maskingCharacter);

        return name.substring(0, lengthOfRemainder) + (new String(mask)) + name.substring(lengthOfName - lengthToMask, lengthOfName);

    }

    public static User maskUser(User user) {
        // todo: add in transfer of whatever other properties may be needed by anonymized views/queries
        User maskedUser = new User();
        maskedUser.setId(user.getId());
        maskedUser.setPhoneNumber(maskPhoneNumber(user.getPhoneNumber()));
        maskedUser.setDisplayName(maskName(user.getDisplayName()));
        maskedUser.setFirstName(maskName(user.getFirstName()));
        maskedUser.setLastName(maskName(user.getLastName()));
        maskedUser.setCreatedDateTime(user.getCreatedDateTime());
        maskedUser.setLanguageCode(user.getLanguageCode());
        maskedUser.setWebProfile(user.hasWebProfile());
        return maskedUser;
    }

    public static List<User> maskUsers(List<User> users) {
        List<User> maskedUsers = new ArrayList<>();
        for (User user : users)
            maskedUsers.add(maskUser(user));
        return maskedUsers;
    }

}
