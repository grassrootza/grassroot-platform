package za.org.grassroot.services.user;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.enums.VerificationCodeType;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.repository.VerificationTokenCodeRepository;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.exception.InvalidOtpException;
import za.org.grassroot.services.exception.UsernamePasswordLoginFailedException;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Random;

/**
 * @author Lesetse Kimwaga
 */
@Service
public class PasswordTokenManager implements PasswordTokenService {

    private static final int TOKEN_LIFE_SPAN_MINUTES = 5;
    private static final int TOKEN_LIFE_SPAN_DAYS = 30;
    private static final int REFRESH_WINDOW_DAYS = 5;

    private static final Logger log = LoggerFactory.getLogger(PasswordTokenManager.class);

    private final VerificationTokenCodeRepository verificationTokenCodeRepository;

    private final UserRepository userRepository;
    private final UserLogRepository userLogRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public PasswordTokenManager(VerificationTokenCodeRepository verificationTokenCodeRepository, UserRepository userRepository,
                                UserLogRepository userLogRepository, PasswordEncoder passwordEncoder) {
        this.verificationTokenCodeRepository = verificationTokenCodeRepository;
        this.userRepository = userRepository;
        this.userLogRepository = userLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public VerificationTokenCode generateShortLivedOTP(String username) {
        Objects.requireNonNull(username);
        if (!PhoneNumberUtil.testInputNumber(username) && !EmailValidator.getInstance().isValid(username)) {
            throw new InvalidPhoneNumberException("Error! Username must be valid msisdn or email");
        }

        VerificationTokenCode token = verificationTokenCodeRepository.findByUsernameAndType(username, VerificationCodeType.SHORT_OTP);
        final String code = String.valueOf(100000 + new SecureRandom().nextInt(999999));

        if (token == null) {
            // no OTP exists, so generate a new one and send it
            token = new VerificationTokenCode(username, code, VerificationCodeType.SHORT_OTP);
            token.setExpiryDateTime(Instant.now().plus(TOKEN_LIFE_SPAN_MINUTES, ChronoUnit.MINUTES));
            return verificationTokenCodeRepository.save(token);
        } else if (Instant.now().isAfter(token.getExpiryDateTime())) {
            // an OTP exists but it is stale
            log.info("found an OTP, but it's stale, time now = {}, expiry time = {}", Instant.now(), token.getExpiryDateTime().toString());
            VerificationTokenCode newToken = new VerificationTokenCode(username, code, VerificationCodeType.SHORT_OTP);
            newToken.setExpiryDateTime(Instant.now().plus(TOKEN_LIFE_SPAN_MINUTES, ChronoUnit.MINUTES));
            verificationTokenCodeRepository.delete(token);
            verificationTokenCodeRepository.save(newToken);
            return newToken;
        } else {
            return token;
        }
    }

    @Override
    @Transactional
    public VerificationTokenCode generateLongLivedAuthCode(String userUid) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        VerificationTokenCode token = verificationTokenCodeRepository.findByUsernameAndType(user.getUsername(),
                VerificationCodeType.LONG_AUTH);

        Random random = new SecureRandom();
        final String code = String.valueOf(new BigInteger(130, random));

        if (token == null) {
            token = new VerificationTokenCode(user.getPhoneNumber(), code, VerificationCodeType.LONG_AUTH);
            token.setExpiryDateTime(Instant.now().plus(TOKEN_LIFE_SPAN_DAYS, ChronoUnit.DAYS));
        } else if (Instant.now().isAfter(token.getExpiryDateTime())) {
            token.setCode(code);
            token.setExpiryDateTime(Instant.now().plus(TOKEN_LIFE_SPAN_DAYS, ChronoUnit.DAYS));
        }

        verificationTokenCodeRepository.save(token);
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationTokenCode fetchLongLivedAuthCode(String phoneNumber) {
       return verificationTokenCodeRepository.findByUsernameAndType(phoneNumber, VerificationCodeType.LONG_AUTH);
    }

    @Override
    @Transactional
    public boolean extendAuthCodeIfExpiring(String phoneNumber, String code) {
        Objects.requireNonNull(phoneNumber);
        Objects.requireNonNull(code);

        VerificationTokenCode token = verificationTokenCodeRepository.findByUsernameAndType(phoneNumber, VerificationCodeType.LONG_AUTH);
        if (token != null && Instant.now().plus(REFRESH_WINDOW_DAYS, ChronoUnit.DAYS).isAfter(token.getExpiryDateTime())) {
            Instant oldExpiry = token.getExpiryDateTime();
            token.setExpiryDateTime(oldExpiry.plus(TOKEN_LIFE_SPAN_DAYS, ChronoUnit.DAYS));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void validateOtp(String username, String otp) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(otp);

        VerificationTokenCode token = verificationTokenCodeRepository.findByUsernameAndType(username, VerificationCodeType.SHORT_OTP);
        if (token == null || Instant.now().isAfter(token.getExpiryDateTime()) || !token.getCode().equals(otp)) {
            throw new InvalidOtpException();
        }
    }


    @Override
    public void validatePassword(String phoneNumber, String password) {
        Objects.requireNonNull(phoneNumber);
        Objects.requireNonNull(password);

        User user = userRepository.findByPhoneNumberAndPhoneNumberNotNull(phoneNumber);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new UsernamePasswordLoginFailedException();
        }
    }

    @Override
    public void validatePwdPhoneOrEmail(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user == null && PhoneNumberUtil.testInputNumber(username)) {
            user = userRepository.findByPhoneNumberAndPhoneNumberNotNull(username);
        }
        if (user == null && EmailValidator.getInstance().isValid(username)) {
            user = userRepository.findByEmailAddressAndEmailAddressNotNull(username);
        }
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new UsernamePasswordLoginFailedException();
        }
    }

    @Override
    @Transactional
    public void changeUserPassword(String userUid, String oldPassword, String newPassword, UserInterfaceType interfaceType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(oldPassword);
        Objects.requireNonNull(newPassword);

        User user = userRepository.findOneByUid(userUid);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new UsernamePasswordLoginFailedException();
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        userRepository.save(user);

        userLogRepository.save(new UserLog(user.getUid(), UserLogType.USER_CHANGED_PASSWORD, null,
                interfaceType));
    }

    @Override
    @Transactional
    public boolean isShortLivedOtpValid(String username, String code) {
        if (username == null || code == null)
            return false;

        log.info("checking for token by username: {}", username);
        // need to use directly as phone number, not attempt to get user first, else fails on registration
        VerificationTokenCode token = verificationTokenCodeRepository.findByUsernameAndType(username, VerificationCodeType.SHORT_OTP);
        if (token == null)
            return false;

        log.info("checking token expiry ...");
        if (Instant.now().isAfter(token.getExpiryDateTime()))
            return false;

        log.info("checking codes: {}, {}", code, token.getCode());
        boolean valid = code.equals(token.getCode());
        if (!valid)
            token.incrementTokenAttempts();

        log.info("returning valid");
        return valid;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLongLiveAuthValid(String phoneNumber, String code) {
        if (phoneNumber == null || code == null) {
            return false;
        }

        User user = userRepository.findByPhoneNumberAndPhoneNumberNotNull(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
        if (user == null) {
            return false;
        }

        VerificationTokenCode token = verificationTokenCodeRepository.findByUsernameAndType(user.getUsername(), VerificationCodeType.LONG_AUTH);
        log.debug("found token for user: {}", token);
        return token != null && code.equals(token.getCode()) && Instant.now().isBefore(token.getExpiryDateTime());
    }

    @Override
    public boolean isExpired(VerificationTokenCode tokenCode){
        return Instant.now().isBefore(tokenCode.getExpiryDateTime());
    }

    @Override
    @Transactional
    public void expireVerificationCode(String userUid, VerificationCodeType type) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(type);

        User user = userRepository.findOneByUid(userUid);
        VerificationTokenCode token = verificationTokenCodeRepository.findByUsernameAndType(user.getUsername(), type);

        if (token != null) {
            token.setExpiryDateTime(Instant.now());
        }
    }
}