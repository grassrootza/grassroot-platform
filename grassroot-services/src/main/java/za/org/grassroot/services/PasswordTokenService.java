package za.org.grassroot.services;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.dto.UserDTO;

/**
 * @author Lesetse Kimwaga
 */
public interface PasswordTokenService {

    int TOKEN_ACCESS_THRESHOLD = 4;

    VerificationTokenCode generateVerificationCode(User user);

    VerificationTokenCode generateVerificationCode(String username);

    VerificationTokenCode generateAndroidVerificationCode(String phoneNumber);


    //todo: remove in future, only for android purposes
    VerificationTokenCode getVerificationCode(String phoneNumber);

    boolean isVerificationCodeValid(User user, String code);

    boolean isVerificationCodeValid(UserDTO userDTO, String code);

    boolean isVerificationCodeValid(String username, String code);

    boolean isExpired(VerificationTokenCode verificationTokenCode);

    void invalidateVerificationCode(User user, String code);

    VerificationTokenCode generateLongLivedCode(User user);



}
