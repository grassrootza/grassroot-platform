package za.org.grassroot.services.user;

import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.VerificationCodeType;

/**
 * @author Lesetse Kimwaga
 */
public interface PasswordTokenService {

    VerificationTokenCode generateShortLivedOTP(String username);

    VerificationTokenCode generateLongLivedAuthCode(String userUid);

    VerificationTokenCode fetchLongLivedAuthCode(String phoneNumber);

    boolean extendAuthCodeIfExpiring(String phoneNumber, String code);

    void validateOtp(String username, String otp);

    void validatePassword(String phoneNumber, String password);

    void validatePwdPhoneOrEmail(String username, String password);

    void changeUserPassword(String userUid, String oldPassword, String newPassword, UserInterfaceType interfaceType);

    boolean isShortLivedOtpValid(String phoneNumber, String code);

    boolean isLongLiveAuthValid(String phoneNumber, String code);

    boolean isExpired(VerificationTokenCode verificationTokenCode);

    void expireVerificationCode(String userUid, VerificationCodeType type);

}
