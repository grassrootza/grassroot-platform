package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;


import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/19.
 */
public class AccountRestControllerTest extends RestAbstractUnitTest{

    String path = "/api/account";

    @InjectMocks
    AccountRestController accountRestController;

    Account account;
    Group group;
    Long groupId;
    Long userId;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(accountRestController).build();
        account = new Account(testAccountName, sessionTestUser);
        group = new Group(testGroupName, sessionTestUser);
        sessionTestUser.setId(5462L);
        group.setId(5352L);
        groupId = group.getId();
        userId = sessionTestUser.getId();
        //The commented lines above seem not to contain the Id's, hence I decided to hard-code the values.
    }

    @Test
    public void addingShouldWork() throws Exception {
        when(accountManagementServiceMock.createAccount(testAccountName)).thenReturn(account);
        when(groupManagementServiceMock.loadGroup(groupId)).thenReturn(group);
        mockMvc.perform(post(path+"/add/{userid}/{groupid}/{accountname}", userId, groupId, testAccountName)).andExpect(status().is2xxSuccessful());
        verify(accountManagementServiceMock).createAccount(testAccountName);
        verify(groupManagementServiceMock).loadGroup(groupId);
    }
}
