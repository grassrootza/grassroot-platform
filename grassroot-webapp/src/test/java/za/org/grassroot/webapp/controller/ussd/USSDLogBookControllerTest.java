package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by luke on 2015/12/18.
 */
public class USSDLogBookControllerTest extends USSDAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(USSDLogBookControllerTest.class);

    private static final String testUserPhone = "0601110001";
    private static final String phoneParam = "msisdn";
    private static final String logBookIdParam = "logbookid";

    private static final Long testLogBookId = 1L;
    private static final String testId = "" + testLogBookId;
    private static final Long dummyId = 1L;

    private static final String path = "/ussd/log/";

    private User testUser;

    @InjectMocks
    USSDLogBookController ussdLogBookController;

    @Mock
    UserRepository userRepository;



    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdLogBookController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpMessageSourceAndGroupUtil(ussdLogBookController, ussdGroupUtil);

        testUser = new User(testUserPhone);

    }

    @Test
    public void groupSelectMenuShouldWorkWithGroup() throws Exception {

        List<Group> testGroups = Arrays.asList(new Group("tg1", testUser),
                                               new Group("tg2", testUser),
                                               new Group("tg3", testUser));
        testUser.setGroupsPartOf(testGroups);
        Page<Group> pageOfGroups = new PageImpl<>(testGroups);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.hasActiveGroupsPartOf(testUser)).thenReturn(true);
        when(groupManagementServiceMock.getPageOfActiveGroups(testUser,0, 3)).thenReturn(pageOfGroups);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).hasActiveGroupsPartOf(testUser);
        verify(groupManagementServiceMock, times(1)).getPageOfActiveGroups(testUser, 0, 3);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void groupSelectMenuShouldWorkWithNoGroup() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.hasActiveGroupsPartOf(testUser)).thenReturn(false);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).hasActiveGroupsPartOf(testUser);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }
    /*

    @Test
    public void listEntriesMenuShouldWorkWithPageNumberSpecified() throws Exception{


        String message = "some message about meeting some other people to" +
                " discuss something important about the community";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<LogBook> testLogBooks = Arrays.asList(new LogBook(dummyId,message,now),
                new LogBook(2L,message,now), new LogBook(3L,message,now), new LogBook(4L,message,now),
                new LogBook(5L,message,now));

        Page<LogBook> dummyPageOfGroups = new PageImpl<>(testLogBooks);


        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        when(userRepository.save(testUser)).thenReturn(testUser);

        when(logBookServiceMock.getAllLogBookEntriesForGroup(dummyId,0,3,true)).thenReturn(dummyPageOfGroups);

        mockMvc.perform(get(path+"list").param(phoneParam, testUserPhone).param("groupId", String.valueOf(dummyId))

                .param("done",String.valueOf(true))).andExpect(status().isOk());

        verify(logBookServiceMock,times(1)).getAllLogBookEntriesForGroup(dummyId,0,3,true);
        verifyNoMoreInteractions(logBookServiceMock);



    }*/



}
