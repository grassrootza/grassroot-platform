package za.org.grassroot.services.user;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.enums.VerificationCodeType;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.repository.VerificationTokenCodeRepository;
import za.org.grassroot.core.specifications.TokenSpecifications;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.exception.InvalidOtpException;
import za.org.grassroot.services.exception.UsernamePasswordLoginFailedException;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * @author Lesetse Kimwaga
 */
@Service @Slf4j
public class PasswordTokenManager implements PasswordTokenService {

    private static final Random RANDOM = new SecureRandom();
    private static final int TOKEN_LIFE_SPAN_MINUTES = 5;
    private static final int TOKEN_LIFE_SPAN_DAYS = 30;
    // making it very long as old device will be discontinued within next few months and several devices
    // have not had a proper refresh (and/or don't have latest client)
    private static final int OLD_TOKEN_REFRESH_WINDOW_DAYS = 365;

    private static final int ENTITY_RESPONSE_TOKEN_LIFE_DAYS = 7;

    private final VerificationTokenCodeRepository verificationTokenCodeRepository;

    private final UserRepository userRepository;
    private final UserLogRepository userLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final MessagingServiceBroker messagingBroker;
    private final MessageSourceAccessor messageSourceAccessor;

    @Autowired
    public PasswordTokenManager(VerificationTokenCodeRepository verificationTokenCodeRepository, UserRepository userRepository,
                                UserLogRepository userLogRepository, PasswordEncoder passwordEncoder, Environment environment,
                                @Qualifier("servicesMessageSourceAccessor") MessageSourceAccessor messageSourceAccessor, MessagingServiceBroker messagingBroker) {

        this.verificationTokenCodeRepository = verificationTokenCodeRepository;
        this.userRepository = userRepository;
        this.userLogRepository = userLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.messagingBroker = messagingBroker;
        this.messageSourceAccessor = messageSourceAccessor;
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
            token = new VerificationTokenCode(username, code, VerificationCodeType.SHORT_OTP, null);
            token.setExpiryDateTime(Instant.now().plus(TOKEN_LIFE_SPAN_MINUTES, ChronoUnit.MINUTES));
            return verificationTokenCodeRepository.save(token);
        } else if (Instant.now().isAfter(token.getExpiryDateTime())) {
            // an OTP exists but it is stale
            log.info("found an OTP, but it's stale, time now = {}, expiry time = {}", Instant.now(), token.getExpiryDateTime().toString());
            VerificationTokenCode newToken = new VerificationTokenCode(username, code, VerificationCodeType.SHORT_OTP, null);
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
            token = new VerificationTokenCode(user.getPhoneNumber(), code, VerificationCodeType.LONG_AUTH, null);
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
    public VerificationTokenCode generateEntityResponseToken(String userUid, String entityUid, boolean forcePersist) {
        Objects.requireNonNull(entityUid);
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));

        VerificationTokenCode checkForPrior = verificationTokenCodeRepository.findOne(
                TokenSpecifications.forUserAndEntity(userUid, entityUid)).orElse(null);

        log.info("prior token: {}", checkForPrior);

        Instant expiry = Instant.now().plus(ENTITY_RESPONSE_TOKEN_LIFE_DAYS, ChronoUnit.DAYS);
        if (checkForPrior != null && checkForPrior.getExpiryDateTime().isAfter(Instant.now())) {
            checkForPrior.setExpiryDateTime(expiry);
            return checkForPrior;
        } else {
            VerificationTokenCode token;
            final String code = String.valueOf(new BigInteger(130, new SecureRandom()));
            if (checkForPrior != null) {
                token = checkForPrior; // pure solution would be to delete, but then have to break TX, so this is lesser of evils
            } else {
                token = new VerificationTokenCode(userUid, code, VerificationCodeType.RESPOND_ENTITY, user.getUid());
                token.setEntityUid(entityUid);
            }

            token.setCode(code);
            token.setExpiryDateTime(expiry);

            if (forcePersist)
                verificationTokenCodeRepository.save(token);

            return token;
        }
    }

    @Override
    @Transactional
    public void generateResponseTokens(Set<String> userUids, String groupUid, String taskUid) {
        Set<VerificationTokenCode> tokens = new HashSet<>();
        userUids.forEach(uid -> tokens.add(generateEntityResponseToken(uid, groupUid, false)));
        if (taskUid != null) {
            userUids.forEach(uid -> tokens.add(generateEntityResponseToken(uid, taskUid, false)));
        }
        verificationTokenCodeRepository.saveAll(tokens);
    }

    @Override
    @Transactional
    public boolean extendAuthCodeIfExpiring(String phoneNumber, String code) {
        Objects.requireNonNull(phoneNumber);
        Objects.requireNonNull(code);

        VerificationTokenCode token = verificationTokenCodeRepository.findByUsernameAndType(phoneNumber, VerificationCodeType.LONG_AUTH);
        boolean validExpiredToken = token != null && code.equals(token.getCode());
        if (validExpiredToken && Instant.now().plus(OLD_TOKEN_REFRESH_WINDOW_DAYS, ChronoUnit.DAYS).isAfter(token.getExpiryDateTime())) {
            Instant oldExpiry = token.getExpiryDateTime();
            token.setExpiryDateTime(oldExpiry.plus(TOKEN_LIFE_SPAN_DAYS, ChronoUnit.DAYS));
            return true;
        } else {
            return false;
        }
    }

    @Override
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public void validateOtp(String username, String otp) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(otp);

        DebugUtil.transactionRequired("");

        if (!isShortLivedOtpValid(username, otp)) {
            throw new InvalidOtpException();
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
    @Transactional(noRollbackFor = InvalidOtpException.class)
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

        log.info("returning {}", valid);
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
        log.info("passed code {}, found token for user: {}", token);
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

    @Override
    @Transactional(readOnly = true)
    public void validateEntityResponseCode(String userUid, String entityUid, String code) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(entityUid);
        Objects.requireNonNull(code);

        Optional<VerificationTokenCode> possibleToken = verificationTokenCodeRepository.findOne(
                TokenSpecifications.forUserAndEntity(userUid, entityUid).and(TokenSpecifications.withCode(code)));

        if (!possibleToken.isPresent() || possibleToken.get().getExpiryDateTime().isBefore(Instant.now())) {
            throw new AccessDeniedException("Error! No matching entity/code combination, or match, but expired");
        }
    }

    @Override
    public void triggerOtp(User user){
        final String message = otpMessage(user.getUsername(),user.getLocale());
        if (environment.acceptsProfiles(Profiles.of(GrassrootApplicationProfiles.PRODUCTION)))
            sendOtp(user, message);
        else
            log.info("OTP message: {}", message);
    }

    @Override
    public String generateRandomPwd() {
        String letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789+@";
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            int index = (int)(RANDOM.nextDouble()*letters.length());
            password.append(letters.substring(index, index + 1));
        }

        return password.toString();
    }

    private String otpMessage(String username,Locale locale) {
        final VerificationTokenCode otp = generateShortLivedOTP(username);
        return messageSourceAccessor.getMessage("text.user.profile.token.message", new String[] {otp.getCode()}, locale);
    }

    private void sendOtp(User user, String message) {
        if (user.hasPhoneNumber()) {
            messagingBroker.sendPrioritySMS(message, user.getPhoneNumber());
        } else {
            messagingBroker.sendEmail(new GrassrootEmail.EmailBuilder()
                    .subject("Your Grassroot verification")
                    .toAddress(user.getEmailAddress())
                    .toName(user.getDisplayName())
                    .content(message).build());
        }
    }

}