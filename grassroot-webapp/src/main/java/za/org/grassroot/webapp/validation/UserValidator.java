package za.org.grassroot.webapp.validation;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;

/**
 * Created by luke on 2015/09/18.
 * Very simple class to validate input of a user and phone number.
 * Note that we do not need to check for duplicate phone numbers (username) or display names that differ from those
 * in persistence, as we only create a user if the phone number has not been entered previously -- if it has, we are
 * just creating an entry in the join table.
 */
@Component("userValidator")
public class UserValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {return User.class.equals(clazz); }

    @Override
    public void validate(Object target, Errors errors) {

        ValidationUtils.rejectIfEmpty(errors, "phoneNumber", "user.enter.error.phoneNumber.empty");

        User inputedUser = (User) target;

        // todo: probably want to control the length of displaynames, but major thing for now is phone numbers

        if (!errors.hasErrors()) {

            if (!PhoneNumberUtil.testInputNumber(inputedUser.getPhoneNumber())) {
                errors.rejectValue("phoneNumber", "user.enter.error.phoneNumber.invalid");
            }

        }


    }

}
