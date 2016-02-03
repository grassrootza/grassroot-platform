package za.org.grassroot.services.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.repository.GroupRepository;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 9/16/15.
 */
@Component
public class TokenGeneratorManager implements TokenGeneratorService {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

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
