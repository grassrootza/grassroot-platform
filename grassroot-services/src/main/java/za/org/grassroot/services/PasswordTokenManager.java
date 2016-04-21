package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.repository.VerificationTokenCodeRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * @author Lesetse Kimwaga
 */
@Service
public class PasswordTokenManager implements PasswordTokenService {

    public static final int TOKEN_LIFE_SPAN_MINUTES = 10;
    public static final int TOKEN_LIFE_SPAN_DAYS =10;
    private Logger log = LoggerFactory.getLogger(PasswordTokenManager.class);


    @Autowired
    private PasswordEncoder passwordTokenEncoder;
    @Autowired
    private VerificationTokenCodeRepository verificationTokenCodeRepository;
    @Autowired
    private UserRepository userRepository;


    @Override
    public VerificationTokenCode generateVerificationCode(User user) {

        VerificationTokenCode verificationTokenCode = verificationTokenCodeRepository.findByUsername(user.getUsername());

        String code = String.valueOf(100000 + new Random().nextInt(999999));

        //String encodedCode = passwordTokenEncoder.encode(code);

        if (verificationTokenCode == null) {
            verificationTokenCode = new VerificationTokenCode(user.getUsername(), code);

        } else {
            verificationTokenCode.setCode(code);
            verificationTokenCode.updateTimeStamp();
            verificationTokenCode.incrementTokenAttempts();
        }

        verificationTokenCode.setExpiryDateTime( getExpiryDateFromNow() );
        verificationTokenCode = verificationTokenCodeRepository.save(verificationTokenCode);

        return verificationTokenCode;
    }






    @Override
    public VerificationTokenCode generateAndroidVerificationCode(String phoneNumber) {

        VerificationTokenCode verificationTokenCode = verificationTokenCodeRepository.findByUsername(phoneNumber);
        Random random = new SecureRandom();
        String code = String.valueOf(100000 + new Random().nextInt(999999));
        if (verificationTokenCode == null) {
            verificationTokenCode = new VerificationTokenCode(PhoneNumberUtil.convertPhoneNumber(phoneNumber), code);

        } else {
            verificationTokenCode.setCode(code);
            verificationTokenCode.updateTimeStamp();
            verificationTokenCode.incrementTokenAttempts();
        }

        verificationTokenCode.setExpiryDateTime( getExpiryDateFromNow() );
        verificationTokenCode = verificationTokenCodeRepository.save(verificationTokenCode);

        return verificationTokenCode;
    }

    @Override
    public VerificationTokenCode getVerificationCode(String phoneNumber) {
       return verificationTokenCodeRepository.findByUsername(phoneNumber);
    }

    @Override
    public VerificationTokenCode generateVerificationCode(String username) {

        User user = userRepository.findByUsername(username);

        if (user != null) {
            return generateVerificationCode(user);
        } else {
            user = userRepository.findByPhoneNumber(PhoneNumberUtil.convertPhoneNumber(username));
            if (user != null) {
                return generateVerificationCode(user);
            } else {
                log.warn("User '{}' with a non existing account found. Cannot create token.", username);
                return null; //We should not create a token for a non existing user. Otherwise Users will be created within an incorrect process
            }
        }

    }


    @Override
    public boolean isVerificationCodeValid(User user, String code) {

        log.info("Trying to match this code (" + code + ") to this user: " + user);

        if (user == null || (code == null || code.trim().equals(""))) {
            return false;
        }
        //String encodedCode = passwordTokenEncoder.encode(code);
        VerificationTokenCode verificationTokenCode = verificationTokenCodeRepository.findByUsernameAndCode(user.getUsername(), code);

        if (verificationTokenCode == null) {
            return false;
        }


        boolean isGreaterThanLifeSpanMinutes =
                Duration.between(LocalDateTime.now(), verificationTokenCode.getCreatedDateTime().toLocalDateTime()).toMinutes()
                        > TOKEN_LIFE_SPAN_MINUTES;

        return !isGreaterThanLifeSpanMinutes;
    }

    @Override
    public boolean isVerificationCodeValid(String username, String code) {
        return isVerificationCodeValid(userRepository.findByUsername(PhoneNumberUtil.convertPhoneNumber(username)),code);
    }

    @Override
    public boolean isVerificationCodeValid(UserDTO userDTO, String code){

        VerificationTokenCode verificationTokenCode =verificationTokenCodeRepository.findByUsernameAndCode((PhoneNumberUtil.convertPhoneNumber(userDTO.getPhoneNumber())),code);
        if (verificationTokenCode == null) {
            return false;
        }
        boolean isGreaterThanLifeSpanMinutes =
                Duration.between(LocalDateTime.now(), verificationTokenCode.getCreatedDateTime().toLocalDateTime()).toMinutes()
                        > TOKEN_LIFE_SPAN_MINUTES;

        return !isGreaterThanLifeSpanMinutes;

    }

    @Override
    public void invalidateVerificationCode(User user, String code) {

        VerificationTokenCode verificationTokenCode = verificationTokenCodeRepository.findByUsernameAndCode(user.getUsername(), code);
        if (verificationTokenCode != null) {
            verificationTokenCode.setCode(null);
            verificationTokenCodeRepository.save(verificationTokenCode);
        }
    }

    private Timestamp getExpiryDateFromNow()
    {
        return Timestamp.valueOf(LocalDateTime.now().plusMinutes(TOKEN_LIFE_SPAN_MINUTES));
    }

    @Override
    public VerificationTokenCode generateLongLivedCode(User user) {

        Random random = new SecureRandom();
        VerificationTokenCode verificationTokenCode = verificationTokenCodeRepository.findByUsername(user.getUsername());
        verificationTokenCode.setCode( String.valueOf( new BigInteger(130, random)));
        verificationTokenCode.setExpiryDateTime(Timestamp.valueOf(LocalDateTime.now().plusDays(TOKEN_LIFE_SPAN_DAYS)));
        verificationTokenCodeRepository.save(verificationTokenCode);

        return verificationTokenCode;

    }

    @Override
    public boolean isExpired(VerificationTokenCode tokenCode){
        return Duration.between(LocalDateTime.now(),tokenCode.getCreatedDateTime().toLocalDateTime()).toDays()>
                TOKEN_LIFE_SPAN_DAYS;
    }


    }


