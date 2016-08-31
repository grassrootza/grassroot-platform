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

        int lengthOfMask = name.length() - 1;

        char[] mask = new char[lengthOfMask];
        Arrays.fill(mask, maskingCharacter);

        return name.substring(0, 1) + (new String(mask));

    }

    public static User maskUser(User user) {
        User maskedUser = User.makeEmpty(user.getUid());
        maskedUser.setPhoneNumber(maskPhoneNumber(user.getPhoneNumber()));
        maskedUser.setDisplayName(maskName(user.getDisplayName()));
        maskedUser.setFirstName(maskName(user.getFirstName()));
        maskedUser.setLastName(maskName(user.getLastName()));
        maskedUser.setLanguageCode(user.getLanguageCode());
        maskedUser.setHasWebProfile(user.isHasWebProfile());
        return maskedUser;
    }

    public static List<User> maskUsers(List<User> users) {
        List<User> maskedUsers = new ArrayList<>();
        for (User user : users)
            maskedUsers.add(maskUser(user));
        return maskedUsers;
    }
}
