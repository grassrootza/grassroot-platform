package za.org.grassroot.webapp.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * todo: wire up the user validator properly
 */
@Component("groupWrapperValidator")
public class GroupWrapperValidator implements Validator {

    private static final Logger log = LoggerFactory.getLogger(GroupWrapperValidator.class);

    @Autowired
    @Qualifier("membershipValidator")
    private Validator memberValidator;

    @Override
    public boolean supports(Class<?> clazz) { return GroupWrapper.class.equals(clazz); }

    @Override
    public void validate(Object target, Errors errors) {

        log.info("groupWrapperValidator ... starting validation ... ");
        GroupWrapper groupWrapper = (GroupWrapper) target;

        log.info("groupWrapperValidator ... got wrapper, memberships ... " + groupWrapper.getAddedMembers());
        int idx = 0;
        for (MembershipInfo member : groupWrapper.getAddedMembers()) {
            errors.pushNestedPath("addedMembers[" + idx + "]");
            try {
                ValidationUtils.invokeValidator(this.memberValidator, member, errors);
            } finally {
                errors.popNestedPath();
                idx++;
            }
        }
    }

}
