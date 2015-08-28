package za.org.grassroot.core.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.VerificationTokenCode;

/**
 * @author Lesetse Kimwaga
 */
public interface VerificationTokenCodeRepository extends CrudRepository<VerificationTokenCode, Long> {

    VerificationTokenCode findByUsernameAndCode(String username, String code);

    VerificationTokenCode findByUsername(String username);
}
