package za.org.grassroot.webapp.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.webapp.model.web.GroupWrapper;

/**
 * Created by luke on 2015/09/18.
 * For the moment, doing the user checks in here, instead of through user input validator, for simplicity.
 * todo: wire up the user validator properly
 */
@Component("groupWrapperValidator")
public class GroupWrapperValidator implements Validator {

    @Autowired
    @Qualifier("userValidator")
    private Validator userValidator;

    @Override
    public boolean supports(Class<?> clazz) { return GroupWrapper.class.equals(clazz); }

    @Override
    public void validate(Object target, Errors errors) {

        // todo: add validation of group name and other things later

        GroupWrapper groupWrapper = (GroupWrapper) target;

        int idx = 0;
        for (User enteredUser : groupWrapper.getAddedMembers()) {
            errors.pushNestedPath("addedMembers[" + idx + "]");
            try {
                ValidationUtils.invokeValidator(this.userValidator, enteredUser, errors);
            } finally {
                errors.popNestedPath();
                idx++;
            }
        }
    }

}
