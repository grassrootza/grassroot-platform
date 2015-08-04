package za.org.grassroot.meeting_organizer.integration.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.meeting_organizer.TestContextConfiguration;
import za.org.grassroot.meeting_organizer.domain.Group;
import za.org.grassroot.meeting_organizer.domain.User;
import za.org.grassroot.meeting_organizer.repository.GroupRepository;
import za.org.grassroot.meeting_organizer.repository.UserRepository;

import javax.transaction.Transactional;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
public class GroupRepositoryTest {

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository; // LSJ: there may be a less expensive way to do this?

    @Test
    public void shouldSaveAndRetrieveGroupData() throws Exception {

        assertThat(groupRepository.count(), is(0L));

        Group groupToCreate = new Group();

        User userToDoTests = new User();
        userToDoTests.setPhoneNumber("56789");
        userRepository.save(userToDoTests);

        groupToCreate.setGroupName("TestGroup");
        groupToCreate.setCreatedByUser(userToDoTests);
        assertNull(groupToCreate.getId());
        assertNull(groupToCreate.getCreatedDateTime());
        groupRepository.save(groupToCreate);

        assertThat(groupRepository.count(), is(1l));
        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertNotNull(groupFromDb.getCreatedDateTime());
        assertThat(groupFromDb.getGroupName(), is("TestGroup"));
        assertThat(groupFromDb.getCreatedByUser().getPhoneNumber(), is("56789"));
    }
}
