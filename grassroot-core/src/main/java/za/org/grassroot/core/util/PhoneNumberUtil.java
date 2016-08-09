package za.org.grassroot.core.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Lesetse Kimwaga
 */

public class PhoneNumberUtil {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberUtil.class);

    public static String convertPhoneNumber(String inputString) throws InvalidPhoneNumberException {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneNumberUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(inputString, "ZA");

            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                return phoneNumberUtil.format(phoneNumber, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164).replace("+", "");
            } else {
                throw new InvalidPhoneNumberException(inputString);
            }
        } catch (NumberParseException e) {
            throw new InvalidPhoneNumberException(inputString);
        }
    }

    // moving this here from User object, better placed here

    public static String invertPhoneNumber(String storedNumber, String joinString) {

        // todo: handle error if number has gotten into database in incorrect format
        // todo: make this much faster, e.g., use a simple regex / split function?

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

    public static String invertPhoneNumber(String storedNumber) {
        return invertPhoneNumber(storedNumber, "");
    }

    public static String formattedNumber(String storedNumber) {
        com.google.i18n.phonenumbers.PhoneNumberUtil util = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        try {
                Phonenumber.PhoneNumber zaNumber = util.parse(storedNumber, "ZA");
                return util.format(zaNumber, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
        } catch (NumberParseException e) {
                log.info("Error parsing stored number! Returning the number given to us");
                return storedNumber;
        }
    }

    public static Map<String, List<String>> splitPhoneNumbers(String userResponse) {

        // todo - aakil - if the number is pasted from contacts it might have spaces in it
        // todo: figure out if possible to have a 00 as delimiter, or just to seperate by 10 digits
        // todo: generally, take the next step from "any delimiter" to "any logical pattern" for bunching numbers

        String userResponseTrimmed = userResponse.replace("\"", ""); // in case the response is passed with quotes around it

        Pattern nonNumericPattern = Pattern.compile("\\d+");
        Matcher numberMatch = nonNumericPattern.matcher(userResponseTrimmed);

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

    /*
    Helper function especially for web input where we can use validators to check and return
     */

    public static boolean testInputNumber(String inputNumber) {

        boolean isNumberValid = true;
        com.google.i18n.phonenumbers.PhoneNumberUtil phoneNumberUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();

        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(inputNumber.trim(), "ZA");
            isNumberValid = (phoneNumberUtil.isValidNumber(phoneNumber) && inputNumber.length() >= 10);
        } catch (NumberParseException e) {
            isNumberValid = false;
        }

        return isNumberValid;

    }


}
