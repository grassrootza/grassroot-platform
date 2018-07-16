package za.org.grassroot.webapp.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author Lesetse Kimwaga
 */
@Slf4j
public class PhoneNumberParsingTest {

    @Test
    public void testPhoneNumberParse() {

        String phoneNumberString = "+27815550000";
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

        try {

            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneNumberString,"ZA");
            log.info("{}", phoneNumber) ;
            log.info("Country Code: {}", phoneNumber.getCountryCode()) ;
            log.info("Country Source: {}", phoneNumber.getCountryCodeSource()) ;
            log.info("National Number: {}", phoneNumber.getNationalNumber()) ;
            assertTrue(phoneNumberUtil.isValidNumber(phoneNumber));

        }catch (Exception e)
        {
            log.error(e.getMessage());
        }

    }


    @Test
    public void testPhoneNumberParse2() throws NumberParseException {

        String phoneNumberString = "0815550001";
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneNumberString,"ZA");
        log.info("{}", phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164));
        log.info("Country Code: {}", phoneNumber.getCountryCode()) ;
        log.info("Country Source: {}", phoneNumber.getCountryCodeSource()) ;
        log.info("National Number: {}", phoneNumber.getNationalNumber()) ;
        log.info("National Number: {}", phoneNumber.getNationalNumber()) ;
        assertTrue(  phoneNumberUtil.isValidNumber(phoneNumber));

    }

    @Test
    public void testPhoneNumberParse3() throws NumberParseException {
        String phoneNumberString = "072 916 6903";
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneNumberString,"ZA");
        log.info("{}", phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164));
        log.info("Country Code: {}", phoneNumber.getCountryCode()) ;
        log.info("Country Source: {}", phoneNumber.getCountryCodeSource()) ;
        log.info("National Number: {}", phoneNumber.getNationalNumber()) ;
        log.info("National Number: {}", phoneNumber.getNationalNumber()) ;
        assertTrue(  phoneNumberUtil.isValidNumber(phoneNumber));
    }
}
