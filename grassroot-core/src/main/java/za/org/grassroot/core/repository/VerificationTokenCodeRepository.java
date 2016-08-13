package za.org.grassroot.core.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.enums.VerificationCodeType;

/**
 * @author Lesetse Kimwaga
 */
public interface VerificationTokenCodeRepository extends CrudRepository<VerificationTokenCode, Long> {

    VerificationTokenCode findByUsernameAndType(String username, VerificationCodeType type);
}
