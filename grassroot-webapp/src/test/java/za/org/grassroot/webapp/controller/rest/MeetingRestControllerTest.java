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
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.MeetingImportance;
import za.org.grassroot.webapp.controller.rest.android.MeetingRestController;

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
    private MeetingRestController meetingRestController;

    private static final String path = "/api/meeting";

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(meetingRestController).build();
    }

    @Test
    public void creatingAMeetingShouldWork() throws Exception {

        Set<String> membersToAdd = new HashSet<>();

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.createMeeting(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP,
                                           testEventTitle, testDateTime, testEventLocation, false,
                EventReminderType.GROUP_CONFIGURED, -1, testEventDescription, membersToAdd, MeetingImportance.ORDINARY))
                .thenReturn(meetingEvent);

        mockMvc.perform(post(path + "/create/{phoneNumber}/{code}/{parentUid}", testUserPhone, testUserCode, testGroup.getUid())
                                .param("title", testEventTitle)
                                .param("description", testEventDescription)
                                .param("eventStartDateTime", testDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                .param("reminderMinutes", String.valueOf(-1))
                                .param("location", testEventLocation))
                .andExpect(status().is2xxSuccessful());

        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).createMeeting(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP,
                                              testEventTitle, testDateTime, testEventLocation, false,
                EventReminderType.GROUP_CONFIGURED, -1, testEventDescription, membersToAdd, MeetingImportance.ORDINARY);
    }

    @Test
    public void updatingAMeetingShoulWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        mockMvc.perform(post(path + "/update/{phoneNumber}/{code}/{meetingUid}", testUserPhone, testUserCode, meetingEvent.getUid())
                .param("title", testEventTitle)
                .param("description", testEventDescription)
                .param("startTime", testDateTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .param("location", testEventLocation))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
    }

    @Test
    public void rsvpingShouldWork() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.loadMeeting(meetingEvent.getUid())).thenReturn(meetingEvent);
        mockMvc.perform(get(path + "/rsvp/{id}/{phoneNumber}/{code}", meetingEvent.getUid(), testUserPhone, testUserCode)
                                .param("response", "Yes"))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).loadMeeting(meetingEvent.getUid());
    }

    @Test
    public void viewRsvpingShouldWork() throws Exception {
        Role role = new Role("ROLE_GROUP_ORGANIZER", meetingEvent.getUid());
        testGroup.addMember(sessionTestUser, role);
        ResponseTotalsDTO testResponseTotalsDTO = ResponseTotalsDTO.makeForTest(40, 20, 10, 0, 70);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.loadMeeting(meetingEvent.getUid())).thenReturn(meetingEvent);
        when(eventLogRepositoryMock.findByEventAndUserAndEventLogType(meetingEvent, sessionTestUser, EventLogType.RSVP))
                .thenReturn(new EventLog(sessionTestUser, meetingEvent, EventLogType.RSVP, EventRSVPResponse.YES));
        when(eventLogBrokerMock.hasUserRespondedToEvent(meetingEvent, sessionTestUser)).thenReturn(false);
        when(eventLogBrokerMock.getResponseCountForEvent(meetingEvent)).thenReturn(testResponseTotalsDTO);
        mockMvc.perform(get(path + "/view/{id}/{phoneNumber}/{code}", meetingEvent.getUid(), testUserPhone, testUserCode))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).loadMeeting(meetingEvent.getUid());
        verify(eventLogRepositoryMock).findByEventAndUserAndEventLogType(meetingEvent, sessionTestUser, EventLogType.RSVP);
        verify(eventLogBrokerMock).getResponseCountForEvent(meetingEvent);

    }
}
