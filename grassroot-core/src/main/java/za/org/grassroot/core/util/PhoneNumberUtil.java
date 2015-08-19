package za.org.grassroot.core.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;

/**
 * @author Lesetse Kimwaga
 */
public class PhoneNumberUtil {

    public static String convertPhoneNumber(String inputString) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneNumberUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(inputString, "ZA");

            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                return phoneNumberUtil.format(phoneNumber, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164).replace("+", "");
            } else {
                throw new RuntimeException("Could not format phone number '" + inputString + "'");
            }

        } catch (NumberParseException e) {
            throw new RuntimeException("Could not format phone number '" + inputString + "'");
        }

    }
}
