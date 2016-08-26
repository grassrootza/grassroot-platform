package za.org.grassroot.webapp.util;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

/**
 * @author Lesetse Kimwaga
 */
public class PhoneNumberParsingTest {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberParsingTest.class);

    @Test
    public void testPhoneNumberParse() throws Exception {

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
    public void testPhoneNumberParse2() throws Exception {

        String phoneNumberString = "0815550001";
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

        try {

            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneNumberString,"ZA");
            log.info("{}", phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164));
            log.info("Country Code: {}", phoneNumber.getCountryCode()) ;
            log.info("Country Source: {}", phoneNumber.getCountryCodeSource()) ;
            log.info("National Number: {}", phoneNumber.getNationalNumber()) ;
            log.info("National Number: {}", phoneNumber.getNationalNumber()) ;
            assertTrue(  phoneNumberUtil.isValidNumber(phoneNumber));


        }catch (Exception e)
        {
            log.error(e.getMessage());
        }

    }    @Test
    public void testPhoneNumberParse3() throws Exception {

        String phoneNumberString = "072 916 6903";
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

        try {

            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneNumberString,"ZA");
            log.info("{}", phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164));
            log.info("Country Code: {}", phoneNumber.getCountryCode()) ;
            log.info("Country Source: {}", phoneNumber.getCountryCodeSource()) ;
            log.info("National Number: {}", phoneNumber.getNationalNumber()) ;
            log.info("National Number: {}", phoneNumber.getNationalNumber()) ;
            assertTrue(  phoneNumberUtil.isValidNumber(phoneNumber));


        }catch (Exception e)
        {
            log.error(e.getMessage());
        }

    }
}
