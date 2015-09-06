package za.org.grassroot.core.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.*;

/**
 * @author Lesetse Kimwaga
 * todo: use a different name so we don't have to include the full path of the Google libraries?
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

    public static Map<String, List<String>> splitPhoneNumbers(String userResponse, String delimiter) {

        // todo: figure out if a more efficient way to return the valid / error split than a map of lists
        // todo: leave the delimiter flexible
        // todo - aakil - also consider asking for a , or something easily entered from keypad # or *
        //                if the number is pasted from contacts it might have spaces in it

        userResponse = userResponse.replace("\"", ""); // in case the response is passed with quotes around it

        Map<String, List<String>> returnMap = new HashMap<>();
        List<String> validNumbers = new ArrayList<>();
        List<String> errorNumbers = new ArrayList<>();

        com.google.i18n.phonenumbers.PhoneNumberUtil phoneNumberUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();

        for (String inputNumber : Arrays.asList(userResponse.split(delimiter))) {
            try {
                Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(inputNumber.trim(), "ZA");
                if (!phoneNumberUtil.isValidNumber(phoneNumber))
                    errorNumbers.add(inputNumber);
                else if (inputNumber.length() < 10) // the util is accepting numbers that are too short, hence adding this
                    errorNumbers.add(inputNumber);
                else
                    validNumbers.add(inputNumber);
            } catch (NumberParseException e) {
                errorNumbers.add(inputNumber);
            }
        }

        returnMap.put("valid", validNumbers);
        returnMap.put("error", errorNumbers);
        return returnMap;
    }


}
