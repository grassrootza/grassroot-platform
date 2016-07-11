package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.dto.TaskDTO;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/27 9:22 AM.
 */
public class TaskRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    TaskRestController taskRestController;

    String path = "/api/task";
    List<TaskDTO> taskList;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskRestController).build();
        // eventLog = new EventLog(sessionTestUser, meetingEvent, EventLogType.RSVP, testEventDescription);
        taskList = new ArrayList<>();
        taskList.add(new TaskDTO(testLogBook, sessionTestUser));
    }

    @Test
    public void gettingTasksShouldWork() throws Exception {
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(taskBrokerMock.fetchGroupTasks(sessionTestUser.getUid(), testGroup.getUid(), null)).
                thenReturn(taskList);
        mockMvc.perform(get(path + "/list/{phoneNumber}/{code}/{id}", testUserPhone, testUserCode, testGroup.getUid())).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(taskBrokerMock, times(1)).fetchGroupTasks(sessionTestUser.getUid(), testGroup.getUid(), null);
    }

}
