package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by paballo on 2016/07/21.
 */

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class SafetyEventRepositoryTest {

    @Autowired
    private SafetyEventRepository safetyEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Test
    public void shouldSaveAndReturnSafetyEvent() throws Exception{

       User user = userRepository.save(new User("0848870765", null, null));
        Group group = groupRepository.save(new Group("group", user));
        SafetyEvent safetyEvent = safetyEventRepository.save(new SafetyEvent(user, group));
        assertNotEquals(null, safetyEvent);
        assertEquals(safetyEvent.getActivatedBy(),user);
        assertNotNull(safetyEvent.getUid());

    }

    @Test
    public void shouldFindByGroup() throws Exception{
      User user = userRepository.save(new User("0848875098", null, null));
        Group group = groupRepository.save(new Group("group", user));
        safetyEventRepository.save(new SafetyEvent(user, group));
        List<SafetyEvent> safetyEvents = safetyEventRepository.findByGroup(group);
        assertNotNull(safetyEvents);
        assertEquals(safetyEvents.get(0).getGroup(),group);
    }


}
