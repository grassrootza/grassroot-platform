package za.org.grassroot.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Created by luke on 2016/02/03.
 */
public class MaskingUtil {

    private static final Logger log = LoggerFactory.getLogger(MaskingUtil.class);

    private static final char maskingCharacter = '*';
    private static final int phoneNumberLength = 11; // todo: put in properties file

    public static String maskPhoneNumber(String phoneNumber) {
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

}
