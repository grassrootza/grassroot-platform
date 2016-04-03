package za.org.grassroot.services.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.repository.GroupRepository;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by aakilomar on 9/16/15.
 */
@Component
public class TokenGeneratorManager implements TokenGeneratorService {

    private Logger logger = LoggerFactory.getLogger(TokenGeneratorManager.class);

    @Autowired
    private GroupRepository groupRepository;
    private AtomicInteger counter;


    @Override
    public int getNextToken() {
        if (counter == null) {
            counter = new AtomicInteger(groupRepository.getMaxTokenValue());
        }
        return counter.addAndGet(7 + (int)(Math.random() * ((29 - 7) + 1)));
    }
}
