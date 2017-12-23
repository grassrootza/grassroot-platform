package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Created by paballo on 2016/04/11.
 */

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class GcmRegistrationRepositoryTest {

    @Autowired
    private GcmRegistrationRepository gcmRegistrationRepository;

    @Autowired
    private UserRepository userRepository;


    @Test
    public void shouldSaveAndReturnGcmRegistration() throws Exception{
        User user = userRepository.save(new User("0848875097", null, null));
        gcmRegistrationRepository.save(new GcmRegistration(user, "xx77f"));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByRegistrationId("xx77f");
        assertNotEquals(null, gcmRegistration);
        assertEquals(gcmRegistration.getRegistrationId(),"xx77f");

    }

    @Test
    public void shouldFindByUser() throws Exception{
        User user = userRepository.save(new User("0848875097", null, null));
        gcmRegistrationRepository.save(new GcmRegistration(user, "xx77f"));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findTopByUserOrderByCreationTimeDesc(user);
        assertNotEquals(null, gcmRegistration);
        assertEquals(gcmRegistration.getUser(),user);
    }


    @Test
    public void shouldFindByUid() throws Exception{
        User user = userRepository.save(new User("0848875097", null, null));
        GcmRegistration gcmRegistration = new GcmRegistration(user, "xx77f");
        String uid = gcmRegistration.getUid();
        gcmRegistrationRepository.save(gcmRegistration);
        assertNotEquals(null, gcmRegistrationRepository.findByUid(uid));

    }
}
