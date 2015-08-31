package za.org.grassroot.services;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;

/**
 * @author Lesetse Kimwaga
 */
public interface PasswordTokenService {

    int TOKEN_ACCESS_THRESHOLD = 4;

    VerificationTokenCode generateVerificationCode(User user);

    VerificationTokenCode generateVerificationCode(String username);

    boolean isVerificationCodeValid(User user, String code);

    boolean isVerificationCodeValid(String username, String code);

    void invalidateVerificationCode(User user, String code);
}
