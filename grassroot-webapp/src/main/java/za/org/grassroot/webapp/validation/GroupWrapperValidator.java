package za.org.grassroot.webapp.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.webapp.model.web.GroupWrapper;

/**
 * Created by luke on 2015/09/18.
 * For the moment, doing the user checks in here, instead of through user input validator, for simplicity.
 */
@Component("groupWrapperValidator")
public class GroupWrapperValidator implements Validator {

    // private static final Logger log = LoggerFactory.getLogger(GroupWrapperValidator.class);

    @Autowired
    @Qualifier("userValidator")
    private Validator userValidator;

    @Override
    public boolean supports(Class<?> clazz) { return GroupWrapper.class.equals(clazz); }

    @Override
    public void validate(Object target, Errors errors) {

        GroupWrapper groupWrapper = (GroupWrapper) target;
        int idx = 0;
        for (MembershipInfo member : groupWrapper.getAddedMembers()) {
            errors.pushNestedPath("addedMembers[" + idx + "]");
            try {
                ValidationUtils.invokeValidator(this.userValidator, member, errors);
            } finally {
                errors.popNestedPath();
                idx++;
            }
        }
    }

}
