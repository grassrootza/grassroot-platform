package za.org.grassroot.core.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // moving this here from User object, better placed here

    public static String invertPhoneNumber(String storedNumber, String joinString) {

        // todo: handle error if number has gotten into database in incorrect format
        // todo: make this much faster, e.g., use a simple regex / split function?

        List<String> numComponents = new ArrayList<>();
        String prefix = String.join("", Arrays.asList("0", storedNumber.substring(2, 4)));
        String midnumbers, finalnumbers;

        try {
            midnumbers = storedNumber.substring(4, 7);
            finalnumbers = storedNumber.substring(7, 11);
        } catch (Exception e) { // in case the string doesn't have enough digits ...
            midnumbers = storedNumber.substring(4);
            finalnumbers = "";
        }

        return String.join(joinString, Arrays.asList(prefix, midnumbers, finalnumbers));

    }



    public static Map<String, List<String>> splitPhoneNumbers(String userResponse) {

        // todo - aakil - if the number is pasted from contacts it might have spaces in it
        // todo: figure out if possible to have a 00 as delimiter, or just to seperate by 10 digits
        // todo: generally, take the next step from "any delimiter" to "any logical pattern" for bunching numbers

        userResponse = userResponse.replace("\"", ""); // in case the response is passed with quotes around it

        Pattern nonNumericPattern = Pattern.compile("\\d+");
        Matcher numberMatch = nonNumericPattern.matcher(userResponse);

        Map<String, List<String>> returnMap = new HashMap<>();
        List<String> validNumbers = new ArrayList<>();
        List<String> errorNumbers = new ArrayList<>();

        com.google.i18n.phonenumbers.PhoneNumberUtil phoneNumberUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();

        while (numberMatch.find()) {
            String inputNumber = numberMatch.group();
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
