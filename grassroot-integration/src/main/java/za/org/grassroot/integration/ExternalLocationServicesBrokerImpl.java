package za.org.grassroot.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;

/**
 * Created by luke on 2017/04/19.
 */
@Service
public class ExternalLocationServicesBrokerImpl implements ExternalLocationServicesBroker {

    @Value("${grassroot.ussd.lbs.url:http://localhost:8080}")
    private String ussdLbsUrl;

    private final UserRepository userRepository;
    private final UserLogRepository userLogRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public ExternalLocationServicesBrokerImpl(UserRepository userRepository, UserLogRepository userLogRepository, RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.userLogRepository = userLogRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional
    public boolean addUssdLocationLookupAllowed(String userUid, UserInterfaceType grantedThroughInterface) {
        UserLog successLog = new UserLog(userUid, UserLogType.LOCATION_PERMISSION_ENABLED,
                "message from server", grantedThroughInterface);
        userLogRepository.save(successLog);
        return true;
    }

    @Override
    public boolean isUssdLocationLookupAllowed(String userUid) {
        return false;
    }

    @Override
    public GeoLocation getUssdLocationForUser(String userUid) {
        return null;
    }
}
