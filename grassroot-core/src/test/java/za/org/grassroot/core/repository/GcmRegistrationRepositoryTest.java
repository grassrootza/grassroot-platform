package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Created by paballo on 2016/04/11.
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class GcmRegistrationRepositoryTest {

    @Autowired
    GcmRegistrationRepository gcmRegistrationRepository;

    @Autowired
    UserRepository userRepository;


    @Test
    public void shouldSaveAndReturnGcmRegistration() throws Exception{
        User user = userRepository.save(new User("0848875097"));
        gcmRegistrationRepository.save(new GcmRegistration(user, "xx77f", Instant.now()));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByRegistrationId("xx77f");
        assertNotEquals(null, gcmRegistration);
        assertEquals(gcmRegistration.getRegistrationId(),"xx77f");

    }

    @Test
    public void shouldFindByUser() throws Exception{
        User user = userRepository.save(new User("0848875097"));
        gcmRegistrationRepository.save(new GcmRegistration(user, "xx77f", Instant.now()));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(user);
        assertNotEquals(null, gcmRegistration);
        assertEquals(gcmRegistration.getUser(),user);
    }


    @Test
    public void shouldFindByUid() throws Exception{
        User user = userRepository.save(new User("0848875097"));
        GcmRegistration gcmRegistration = new GcmRegistration(user, "xx77f", Instant.now());
        String uid = gcmRegistration.getUid();
        gcmRegistrationRepository.save(gcmRegistration);
        assertNotEquals(null, gcmRegistrationRepository.findByUid(uid));

    }
}
