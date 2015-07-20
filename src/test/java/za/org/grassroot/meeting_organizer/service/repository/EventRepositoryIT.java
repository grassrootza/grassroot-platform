package za.org.grassroot.meeting_organizer.service.repository;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.meeting_organizer.Application;
import za.org.grassroot.meeting_organizer.DbUnitConfig;
import za.org.grassroot.meeting_organizer.model.Event;
import za.org.grassroot.meeting_organizer.model.Group;
import za.org.grassroot.meeting_organizer.model.User;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * Created by luke on 2015/07/19.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Application.class, DbUnitConfig.class})
@TestExecutionListeners( listeners = DbUnitTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DatabaseSetup("/db/empty_tables.xml") // As with UserRepo test, init empty tables each time
public class EventRepositoryIT {

    @Autowired
    EventRepository eventRepository;

    @Autowired
    GroupRepository groupRepository; // LSJ: as with group repo tests, may be less expensive way to do this

    @Autowired
    UserRepository userRepository;

    User userToDoTests;
    Group groupToDoTests;

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
