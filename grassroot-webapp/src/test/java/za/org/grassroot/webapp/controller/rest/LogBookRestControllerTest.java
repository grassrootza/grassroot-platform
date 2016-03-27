package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/23.
 */
public class LogBookRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    LogBookRestController logBookRestController;

    String path = "/api/logbook";

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(logBookRestController).build();
    }

    @Test
    public void settingCompleteShouldWork() throws Exception {

        testLogBook.setId(6L);
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(logBookServiceMock.load(testLogBook.getId())).thenReturn(testLogBook);
        mockMvc.perform(get(path + "/complete/do/{id}/{phoneNumber}/{code}", testLogBook.getId(), testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(logBookServiceMock).load(testLogBook.getId());
    }
}
