package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.domain.VerificationTokenCode_;

public final class TokenSpecifications {

    public static Specification<VerificationTokenCode> forUserAndEntity(String userUid, String entityUid) {
        Specification<VerificationTokenCode> forUser = (root, query, cb) -> cb.equal(root.get(VerificationTokenCode_.userUid),
            userUid);
        Specification<VerificationTokenCode> forEntity = (root, query, cb) -> cb.equal(root.get(VerificationTokenCode_.entityUid),
            entityUid);
        return Specification.where(forUser).and(forEntity);
    }

    public static Specification<VerificationTokenCode> withCode(String code) {
        return (root, query, cb) -> cb.equal(root.get(VerificationTokenCode_.code), code);
    }

}
