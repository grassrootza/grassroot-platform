package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.webapp.controller.android1.TodoLegacyController;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/23.
 */
public class TodoRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    private TodoLegacyController todoRestController;

    private String path = "/api/todo";

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(todoRestController).build();
    }

    @Test
    public void settingCompleteShouldWork() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(todoBrokerMock.load(TEST_TO_DO.getUid())).thenReturn(TEST_TO_DO);
        mockMvc.perform(get(path + "/complete/{phoneNumber}/{code}/{id}", testUserPhone, testUserCode, TEST_TO_DO.getUid())).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(todoBrokerMock).load(TEST_TO_DO.getUid());
    }
}
