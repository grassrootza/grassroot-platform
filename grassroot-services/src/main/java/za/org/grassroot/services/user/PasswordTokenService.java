package za.org.grassroot.services.user;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.VerificationCodeType;

import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
public interface PasswordTokenService {

    void triggerOtp(User user);

    String generateRandomPwd();

    VerificationTokenCode generateShortLivedOTP(String username);

    VerificationTokenCode generateLongLivedAuthCode(String userUid);

    VerificationTokenCode fetchLongLivedAuthCode(String phoneNumber);

    VerificationTokenCode generateEntityResponseToken(String userUid, String entityUid, boolean forcePersist);

    void generateResponseTokens(Set<String> userUids, String groupUid, String taskUid);

    boolean extendAuthCodeIfExpiring(String phoneNumber, String code);

    void validateOtp(String username, String otp);

    void validatePwdPhoneOrEmail(String username, String password);

    void changeUserPassword(String userUid, String oldPassword, String newPassword, UserInterfaceType interfaceType);

    boolean isShortLivedOtpValid(String username, String code);

    boolean isLongLiveAuthValid(String phoneNumber, String code);

    boolean isExpired(VerificationTokenCode verificationTokenCode);

    void expireVerificationCode(String userUid, VerificationCodeType type);

    void validateEntityResponseCode(String userUid, String entityUid, String code);

}
