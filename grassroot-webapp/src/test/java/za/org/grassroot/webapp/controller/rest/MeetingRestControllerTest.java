package za.org.grassroot.webapp.controller.rest;

import org.apache.commons.logging.Log;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;

import java.sql.Timestamp;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/22.
 */
public class MeetingRestControllerTest extends RestAbstractUnitTest {

    Logger logger = Logger.getLogger(getClass().getCanonicalName());
    @InjectMocks
    MeetingRestController meetingRestController;

    String path = "/api/meeting";

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(meetingRestController).build();
    }

    @Test
    public void rsvpingShouldWork() throws Exception {
        Group group = new Group("test_group", sessionTestUser);
        Timestamp timestamp = Timestamp.valueOf(DateTimeUtil.parseDateTime("1500"));
        meetingEvent.setId(34895L);
        logger.info("The id of this event is: " + meetingEvent.getId());
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
       // when(eventManagementServiceMock.loadEvent(meeting.getId())).thenReturn(meeting);
        mockMvc.perform(get(path + "/rsvp/{id}/{phoneNumber}/{code}", meetingEvent.getId(), testUserPhone, testUserCode).param("response", "Yes")).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
       // verify(eventManagementServiceMock).loadEvent(meeting.getId());
    }
}
