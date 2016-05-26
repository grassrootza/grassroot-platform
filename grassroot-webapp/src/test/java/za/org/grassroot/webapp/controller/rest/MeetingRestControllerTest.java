package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.EventReminderType;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/22.
 */
public class MeetingRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    MeetingRestController meetingRestController;

    String path = "/api/meeting";
    EventLog testEventLog = new EventLog(sessionTestUser, meetingEvent, EventLogType.EventCreated, "test notification");
    ResponseTotalsDTO testResponseTotalsDTO = new ResponseTotalsDTO();

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(meetingRestController).build();
    }

    @Test
    public void creatingAMeetingShouldWork() throws Exception {

        Set<String> membersToAdd = new HashSet<>();

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.createMeeting(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP,
                                           testEventTitle, testDateTime, testEventLocation, true, true, true, EventReminderType.CUSTOM, 5, testEventDescription, membersToAdd)).thenReturn(meetingEvent);
        mockMvc.perform(post(path + "/create/{phoneNumber}/{code}/{parentUid}", testUserPhone, testUserCode, testGroup.getUid())
                                .param("title", testEventTitle)
                                .param("description", testEventDescription)
                                .param("eventStartDateTime", testDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                .param("reminderMinutes", String.valueOf(-1))
                                .param("location", testEventLocation))
                .andExpect(status().is2xxSuccessful());

        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventBrokerMock).createMeeting(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP,
                                              testEventTitle, testDateTime, testEventLocation, false, true, false,
                                              EventReminderType.GROUP_CONFIGURED, -1, testEventDescription, membersToAdd);
    }

    @Test
    public void updatingAMeetingShoulWork() throws Exception {

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        mockMvc.perform(post(path + "/update/{id}/{phoneNumber}/{code}", meetingEvent.getUid(), testUserPhone, testUserCode).param("title", testEventTitle).param("description", testEventDescription).param("startTime", String.valueOf(testInstant)).param("notifyGroup", String.valueOf(true)).param("reminderMins", String.valueOf(5)).param("location", testEventLocation).param("includeSubGroups", String.valueOf(true)).param("rsvpRequired", String.valueOf(true))).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
    }
    @Test
    public void rsvpingShouldWork() throws Exception {

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.loadMeeting(meetingEvent.getUid())).thenReturn(meetingEvent);
        mockMvc.perform(get(path + "/rsvp/{id}/{phoneNumber}/{code}", meetingEvent.getUid(), testUserPhone, testUserCode)
                                .param("response", "Yes"))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventBrokerMock).loadMeeting(meetingEvent.getUid());
    }

    @Test
    public void viewRsvpingShouldWork() throws Exception {

        Role role = new Role("ROLE_GROUP_ORGANIZER", meetingEvent.getUid());
        testGroup.addMember(sessionTestUser, role);

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.loadMeeting(meetingEvent.getUid())).thenReturn(meetingEvent);
        when(eventLogManagementServiceMock.getEventLogOfUser(meetingEvent, sessionTestUser, EventLogType.EventRSVP)).thenReturn(testEventLog);
        when(eventLogManagementServiceMock.userRsvpForEvent(meetingEvent, sessionTestUser)).thenReturn(false);
        when(eventLogManagementServiceMock.getResponseCountForEvent(meetingEvent)).thenReturn(testResponseTotalsDTO);
        mockMvc.perform(get(path + "/view/{id}/{phoneNumber}/{code}", meetingEvent.getUid(), testUserPhone, testUserCode))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventBrokerMock).loadMeeting(meetingEvent.getUid());
        verify(eventLogManagementServiceMock).getEventLogOfUser(meetingEvent, sessionTestUser, EventLogType.EventRSVP);
        verify(eventLogManagementServiceMock).userRsvpForEvent(meetingEvent, sessionTestUser);
        verify(eventLogManagementServiceMock).getResponseCountForEvent(meetingEvent);

    }
}
