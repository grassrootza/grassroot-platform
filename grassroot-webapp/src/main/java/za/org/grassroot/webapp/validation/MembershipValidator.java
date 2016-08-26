package za.org.grassroot.webapp.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.MembershipInfo;

/**
 * Created by luke on 2015/09/18.
 * Very simple class to validate input of a user and phone number.
 * Note that we do not need to check for duplicate phone numbers (username) or display names that differ from those
 * in persistence, as we only create a user if the phone number has not been entered previously -- if it has, we are
 * just creating an entry in the join table.
 */
@Component("membershipValidator")
public class MembershipValidator implements Validator {

    private static final Logger log = LoggerFactory.getLogger(MembershipValidator.class);

    @Override
    public boolean supports(Class<?> clazz) {return MembershipInfo.class.equals(clazz); }

    @Override
    public void validate(Object target, Errors errors) {

        log.info("membershipValidator ... checking this member ... " + target.toString());
        ValidationUtils.rejectIfEmpty(errors, "phoneNumber", "user.enter.error.phoneNumber.empty");

        MembershipInfo inputedMember = (MembershipInfo) target;

        // todo: probably want to control the length of displaynames, but major thing for now is phone numbers

        if (!errors.hasErrors()) {

            log.info("Checking this inputNumber ... " + inputedMember.getPhoneNumber());
            if (!PhoneNumberUtil.testInputNumber(inputedMember.getPhoneNumber())) {
                errors.rejectValue("phoneNumber", "user.enter.error.phoneNumber.invalid");
            }

        }


    }

}
