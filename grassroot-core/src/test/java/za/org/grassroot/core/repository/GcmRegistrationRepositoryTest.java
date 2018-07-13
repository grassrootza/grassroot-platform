package za.org.grassroot.core.repository;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Created by paballo on 2016/04/11.
 */

@Slf4j @RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = TestContextConfiguration.class)
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
