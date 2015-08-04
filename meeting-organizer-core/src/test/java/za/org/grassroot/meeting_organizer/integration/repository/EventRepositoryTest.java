package za.org.grassroot.meeting_organizer.integration.repository;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import za.org.grassroot.meeting_organizer.MeetingOrganizerApplication;

import za.org.grassroot.meeting_organizer.TestContextConfiguration;
import za.org.grassroot.meeting_organizer.domain.Event;
import za.org.grassroot.meeting_organizer.domain.Group;
import za.org.grassroot.meeting_organizer.domain.User;
import za.org.grassroot.meeting_organizer.repository.EventRepository;
import za.org.grassroot.meeting_organizer.repository.GroupRepository;
import za.org.grassroot.meeting_organizer.repository.UserRepository;

import javax.transaction.Transactional;
import java.sql.Timestamp;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
public class EventRepositoryTest {

    @Autowired
    EventRepository eventRepository;

    @Autowired
    GroupRepository groupRepository; // LSJ: as with group repo tests, may be less expensive way to do this

    @Autowired
    UserRepository userRepository;


    @Test
    public void shouldSaveAndRetrieveEventData() throws Exception {

        assertThat(eventRepository.count(), is(0L));

        Event eventToCreate = new Event();
        User userToDoTests = new User();
        Group groupToDoTests = new Group();

        userToDoTests.setPhoneNumber("55555");
        userRepository.save(userToDoTests);
        groupToDoTests.setGroupName("Test Group");
        groupToDoTests.setCreatedByUser(userToDoTests);
        groupRepository.save(groupToDoTests);

        Timestamp testStartDateTime = Timestamp.valueOf("2015-08-18 10:00:00.0");

        eventToCreate.setCreatedByUser(userToDoTests);
        eventToCreate.setAppliesToGroup(groupToDoTests);
        eventToCreate.setEventLocation("The testing location");
        eventToCreate.setEventStartDateTime(testStartDateTime);
        assertNull(eventToCreate.getId());
        assertNull(eventToCreate.getCreatedDateTime());

        eventRepository.save(eventToCreate);

        assertThat(userRepository.count(), is(1l));

        Event eventFromDb = eventRepository.findAll().iterator().next();

        assertNotNull(eventFromDb.getId());
        assertNotNull(eventFromDb.getCreatedDateTime());
        assertNotNull(eventFromDb.getEventLocation());

        assertThat(eventFromDb.getEventLocation(), is("The testing location"));
        assertThat(eventFromDb.getAppliesToGroup().getGroupName(), is("Test Group"));
        assertThat(eventFromDb.getCreatedByUser().getPhoneNumber(), is("55555"));
        assertThat(eventFromDb.getEventStartDateTime(), is(testStartDateTime));
    }

}
