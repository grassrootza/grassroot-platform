package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.enums.EventLogType;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/27 9:22 AM.
 */
public class TaskRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    TaskRestController taskRestController;

    String path = "/api/task";
    EventLog eventLog;
    List<LogBook> logBookList;
    List<Event> eventList;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(taskRestController).build();
        // eventLog = new EventLog(sessionTestUser, meetingEvent, EventLogType.EventRSVP, testEventDescription);
        logBookList = new ArrayList<>();
        logBookList.add(testLogBook);
        eventList = new ArrayList<>();
    }

    @Test
    public void gettingTasksShouldWork() throws Exception {

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        // when(eventManagementServiceMock.findByAppliesToGroup(testGroup)).thenReturn(eventList);
        when(eventLogManagementServiceMock.getEventLogOfUser(meetingEvent, sessionTestUser, EventLogType.EventRSVP)).thenReturn(eventLog);
        when(eventLogManagementServiceMock.userRsvpForEvent(meetingEvent, sessionTestUser)).thenReturn(true);
        when(logBookServiceMock.getAllLogBookEntriesForGroup(testGroup.getId())).thenReturn(logBookList);
        mockMvc.perform(get(path + "/list/{id}/{phoneNumber}/{code}", testGroup.getUid(), testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(groupBrokerMock).load(testGroup.getUid());
    }

}
