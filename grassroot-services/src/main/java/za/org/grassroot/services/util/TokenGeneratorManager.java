package za.org.grassroot.services.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.repository.GroupRepository;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Created by aakilomar on 9/16/15.
 */
@Component
public class TokenGeneratorManager implements TokenGeneratorService {
    private static final int retryLimit = 3000;

    @Autowired
    private GroupRepository groupRepository;

    private final Random random = new Random();

    @Override
    public String getNextToken() {
        Set<String> tokenCodes = new HashSet<>(groupRepository.findAllTokenCodes());

        for (int i = 0; i < retryLimit; i++) {
            String code = generateTokenCode(tokenCodes.size());
            if (!tokenCodes.contains(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate random group token code; retry limit reached: " + retryLimit);
    }

    private String generateTokenCode(int currentTokenCodeCount) {
        int numberOfDigits = 4;
        // in case we have large number of groups already, let's raise to 5 digits...
        if (currentTokenCodeCount > 9500) {
            numberOfDigits++;
        }
        int maxCodeInt = (int) Math.pow(10, numberOfDigits);
        int rndValue = random.nextInt(maxCodeInt);
        //exclude 911 as it reserved for safety button activation
        while(rndValue == 911){
            rndValue =random.nextInt(maxCodeInt);
        }

        return String.format("%0" + numberOfDigits + "d", rndValue);
    }
}
