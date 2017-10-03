package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.webapp.controller.android1.TaskRestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TaskRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    private TaskRestController taskRestController;

    private String path = "/api/task";
    private List<TaskDTO> taskList;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskRestController).build();
        // eventLog = new EventLog(sessionTestUser, meetingEvent, EventLogType.RSVP, testEventDescription);
        taskList = new ArrayList<>();
        taskList.add(new TaskDTO(TEST_TO_DO, sessionTestUser));
    }

    @Test
    public void gettingTasksShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(taskBrokerMock.fetchGroupTasks(sessionTestUser.getUid(), testGroup.getUid(), null)).
                thenReturn(new ChangedSinceData<>(taskList, Collections.EMPTY_SET));
        mockMvc.perform(get(path + "/list/{phoneNumber}/{code}/{id}", testUserPhone, testUserCode, testGroup.getUid())).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(taskBrokerMock, times(1)).fetchGroupTasks(sessionTestUser.getUid(), testGroup.getUid(), null);
    }

}
