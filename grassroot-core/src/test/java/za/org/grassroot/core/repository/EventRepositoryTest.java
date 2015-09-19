package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.Date;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
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

    @Test
    public void testMinimumEqual() {
        Event e1 = new Event();
        Event e2 = new Event();
        assertEquals(true,e1.minimumEquals(e2));
        e1.setEventLocation("location");
        assertEquals(false, e1.minimumEquals(e2));
        e2.setEventLocation(e1.getEventLocation());
        assertEquals(true, e1.minimumEquals(e2));
        e1.setName("name");
        assertEquals(false, e1.minimumEquals(e2));
        e2.setName(e1.getName());
        assertEquals(true, e1.minimumEquals(e2));
        e1.setDateTimeString("31th 7pm");
        assertEquals(false, e1.minimumEquals(e2));
        e2.setDateTimeString(e1.getDateTimeString());
        assertEquals(true, e1.minimumEquals(e2));
        e1.setEventStartDateTime(new Timestamp(new Date().getTime()));
        assertEquals(false, e1.minimumEquals(e2));
        e2.setEventStartDateTime(e1.getEventStartDateTime());
        assertEquals(true, e1.minimumEquals(e2));

    }


}
