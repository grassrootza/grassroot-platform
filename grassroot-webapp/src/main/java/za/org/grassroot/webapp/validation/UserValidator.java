package za.org.grassroot.webapp.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.MembershipInfo;

/**
 * Created by luke on 2015/09/18.
 * Very simple class to validate input of a user and phone number.
 * Note that we do not need to check for duplicate phone numbers (username) or display names that differ from those
 * in persistence, as we only create a user if the phone number has not been entered previously -- if it has, we are
 * just creating an entry in the join table.
 */
@Component("userValidator")
public class UserValidator implements Validator {

    private static final Logger log = LoggerFactory.getLogger(UserValidator.class);

    @Override
    public boolean supports(Class<?> clazz) {return User.class.equals(clazz); }

    @Override
    public void validate(Object target, Errors errors) {

        ValidationUtils.rejectIfEmpty(errors, "phoneNumber", "user.enter.error.phoneNumber.empty");

        // we validate the same thing on both of these, hence ...
        if (!errors.hasErrors()) {

            User user;
            MembershipInfo member;

            if (target instanceof User) {
                user = (User) target;
                validate(errors, user.getPhoneNumber(), user.getDisplayName());
            } else if (target instanceof MembershipInfo) {
                member = (MembershipInfo) target;
                validate(errors, member.getPhoneNumber(), member.getDisplayName());
            } else {
                throw new UnsupportedOperationException("Error! User validator passed neither user nor member");
            }
        }
    }

    private void validate(Errors errors, final String phoneNumber, final String displayName) {
        if (!PhoneNumberUtil.testInputNumber(phoneNumber)) {
            errors.rejectValue("phoneNumber", "user.enter.error.phoneNumber.invalid");
        }

        if (!StringUtils.isEmpty(displayName) && displayName.length() > 25) {
            errors.rejectValue("displayName", "user.enter.displayName.length");
        }
    }
}
