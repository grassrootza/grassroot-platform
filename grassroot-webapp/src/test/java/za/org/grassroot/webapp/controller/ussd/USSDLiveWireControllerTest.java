package za.org.grassroot.webapp.controller.ussd;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by gaven on 2017/05/16.
 */
public class USSDLiveWireControllerTest extends USSDAbstractUnitTest {

    private static final String testPhone = "27894345000";
    private static final String userName = "User";
    private static final String groupTest = "Group";
    private static final String phoneInput = "msisdn";
    //private static final String liveWireMenu = "/ussd/livewire/";
    private static final int pageSize = 3;
    private User testUser = new User(testPhone, userName);
    Group groups = new Group("", testUser);

    @InjectMocks
    private USSDLiveWireController ussdLiveWireController;

    @Before
    public void SetUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ussdLiveWireController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();

        wireUpMessageSourceAndGroupUtil(ussdLiveWireController);
    }

    @Test
    public void shouldSelectContactForMeeting() throws Exception {

        Meeting meeting = new Meeting("", Instant.now(),
                testUser, groups, "");

        LiveWireAlert alert = new LiveWireAlert.Builder()
                .creatingUser(testUser)
                .type(LiveWireAlertType.MEETING)
                .meeting(meeting)
                .build();

        when(userManagementServiceMock.findByInputNumber(testPhone)).
                thenReturn(testUser);
        when(liveWireBrokerMock.create(testUser.getUid(), LiveWireAlertType.MEETING,
                meeting.getUid())).thenReturn(alert.getUid());
        when(liveWireBrokerMock.load(alert.getUid())).thenReturn(alert);

        mockMvc.perform(get("/ussd/livewire/mtg").
                param(phoneInput, testPhone)
                .param("mtgUid", meeting.getUid())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).
                findByInputNumber(testPhone);
        verify(cacheUtilManagerMock, times(1)).
                putUssdMenuForUser(testPhone, "livewire/" + "mtg"
                        + "?alertUid=" + alert.getUid());


    }

    @Test
    public void shouldSelectGroupInstantAlert() throws Exception {

        Group group1 = new Group("", testUser);
        List<Group> groupAlert = Arrays.asList(groups, group1);

        when(userManagementServiceMock.findByInputNumber(testPhone)).
                thenReturn(testUser);
        when(liveWireBrokerMock.groupsForInstantAlert(testUser.getUid()
                , 0, pageSize))
                .thenReturn(groupAlert);

        when(liveWireBrokerMock.countGroupsForInstantAlert(testUser.getUid()))
                .thenReturn(1L);

        mockMvc.perform(get("/ussd/livewire/instant").param(phoneInput, testPhone))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).
                findByInputNumber(testPhone);

        verify(liveWireBrokerMock, times(1)).
                countGroupsForInstantAlert(testUser.getUid());

        verify(liveWireBrokerMock, times(1)).
                groupsForInstantAlert(testUser.getUid(), null, pageSize);

    }

    @Test
    public void shouldPromptToRegisterAsContact() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testPhone)).
                thenReturn(testUser);

        mockMvc.perform(get("/ussd/livewire/register").
                param(phoneInput, testPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).
                findByInputNumber(testPhone);

    }

    @Test
    public void shouldRegisterAsLiveWireContact() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testPhone)).
                thenReturn(testUser);

        mockMvc.perform(get("/ussd/livewire/register/do").
                param(phoneInput, testPhone).
                param("location",
                        "on")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).
                findByInputNumber(testPhone);

        verify(liveWireBrokerMock, times(1)).
                updateUserLiveWireContactStatus(testUser.getUid(),
                        true, UserInterfaceType.USSD);
        verify(liveWireBrokerMock, times(1)).
                trackLocationForLiveWireContact(testUser.getUid(),
                        UserInterfaceType.USSD);

        mockMvc.perform(get("/ussd/livewire/register/do").
                param(phoneInput, testPhone).
                param("location",
                        "off")).
                andExpect(status().isOk());

    }

    @Test
    public void shouldFindGroupChosen() throws Exception {


        when(userManagementServiceMock.findByInputNumber(testPhone)).
                thenReturn(testUser);
        when(liveWireBrokerMock.create(testUser.getUid(), LiveWireAlertType.INSTANT, groups.getUid())).
                thenReturn(groups.getUid());

        mockMvc.perform(get("/ussd/livewire/group")
                .param(phoneInput, testPhone)
                .param("groupUid", groups.getUid())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).
                findByInputNumber(testPhone);

        verify(liveWireBrokerMock, times(1)).
                create(testUser.getUid(), LiveWireAlertType.INSTANT, groups.getUid());

        verify(cacheUtilManagerMock, times(1)).
                putUssdMenuForUser(testPhone, "livewire/" + "group"
                        + "?alertUid=" + groups.getUid());


    }

    @Test
    public void shouldEnterContactPersonNumber() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testPhone,
                "livewire/" + "contact/phone" +
                        "?alertUid=" + "alert123")).
                thenReturn(testUser);


        mockMvc.perform(get("/ussd/livewire/contact/phone").
                param(phoneInput, testPhone).
                param("alertUid", "alert123")).
                andExpect(status().isOk());


        verify(userManagementServiceMock, times(1)).
                findByInputNumber(testPhone,
                        "livewire/" + "contact/phone" +
                                "?alertUid=" + "alert123");

    }


    @Test
    public void shouldConfirmAlert() throws Exception {

        LiveWireAlert alert = new LiveWireAlert.Builder()
                .creatingUser(testUser)
                .type(LiveWireAlertType.MEETING)
                .description("describe")
                .contactUser(testUser).contactName("gaven")
                .build();

        String[] alerts = new String[]{alert.getDescription(), alert.getContactUser().getName()
        };

        when(userManagementServiceMock.findByInputNumber(testPhone)).
                thenReturn(testUser);

        when(liveWireBrokerMock.load(alert.getUid()))
                .thenReturn(alert);

        mockMvc.perform(get("/ussd/livewire/confirm").
                param(phoneInput, testPhone).
                param("alertUid", alert.getUid()).
                param("request", "request").
                param("priorInput", "priorInput").
                param("revisingContact", "on").
                param("contactUid", "contact")).
                andExpect(status().isOk());

        verify(cacheUtilManagerMock, times(1)).
                putUssdMenuForUser(testPhone, "livewire/" + "confirm" +
                        "?alertUid=" + alert.getUid() + "&priorInput=" + "priorInput" +
                        "&revisingContact=true&contactUid=" + "contact");

        verify(liveWireBrokerMock, times(1)).
                load(alert.getUid());

        verify(liveWireBrokerMock, times(1)).
                updateContactUser(testUser.getUid(), alert.getUid(),
                        "contact", "priorInput");

        mockMvc.perform(get("/ussd/livewire/confirm").
                param(phoneInput, testPhone).
                param("alertUid", alert.getUid()).
                param("request", "request").
                param("priorInput", "priorInput").
                param("revisingContact", "off").
                param("contactUid", "contact")).
                andExpect(status().isOk());


        verify(cacheUtilManagerMock,times(1)).
                putUssdMenuForUser(testPhone,"livewire/"
                        + "confirm" + "?alertUid=" + alert.getUid() +
                        "&priorInput=" + "priorInput");

        verify(liveWireBrokerMock,times(1)).
                updateDescription(testUser.getUid(),alert.getUid(),
                        "priorInput");



    }

    @Test
    public void shouldSendAlert() throws Exception {

        LiveWireAlert alert = new LiveWireAlert.Builder()
                 .creatingUser(testUser)
                 .type(LiveWireAlertType.MEETING)
                 .build();

        when(userManagementServiceMock.findByInputNumber(testPhone, null))
                .thenReturn(testUser);

        when(liveWireBrokerMock.load(alert.getUid())).
                thenReturn(alert);

        mockMvc.perform(get("/ussd/livewire/send").
                param(phoneInput, testPhone).
                param("alertUid", alert.getUid()).
                param("location", "on")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).
                findByInputNumber(testPhone, null);

        verify(liveWireBrokerMock, times(1))
                .setAlertComplete(eq(testUser.getUid()),
                        eq(alert.getUid()), any(Instant.class));

        verify(liveWireBrokerMock,times(1)).
                load(alert.getUid());



        verify(liveWireBrokerMock, times(1)).
                addLocationToAlert(testUser.getUid(),
                alert.getUid(), null, UserInterfaceType.USSD);

        verify(dataSubscriberBrokerMock, times(1)).
                countPushEmails();

        mockMvc.perform(get("/ussd/livewire/send").
                param(phoneInput, testPhone).
                param("alertUid", alert.getUid()).
                param("location", "off")).
                andExpect(status().isOk());


    }


    @Test
    public void shouldEnterDescription() throws Exception {

        when(userManagementServiceMock.
                findByInputNumber(testPhone,"livewire/" +
                        "description" + "?alertUid=" + "alert" + "&priorInput=" + "priorInput" +
                        "&contactUid=" + "contact")).
                thenReturn(testUser);

        mockMvc.perform(get("/ussd/livewire/description").
                param(phoneInput,testPhone).
                param("alertUid","alert").
                param("request","request").
                param("contactUid","contact").
                param("priorInput","priorInput")).
                andExpect(status().isOk());

        verify(liveWireBrokerMock,times(1))
        .updateContactUser(testUser.getUid()
                ,"alert","contact","priorInput");

        verify(userManagementServiceMock,times(1)).
                findByInputNumber(testPhone,"livewire/" +
                        "description" + "?alertUid=" + "alert" +
                        "&priorInput=" + "priorInput" + "&contactUid=" + "contact");


    }

    @Test
    public void shouldEnterContactPersonName() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testPhone)).
                thenReturn(testUser);

        mockMvc.perform(get("/ussd/livewire/contact/name").
                param(phoneInput,testPhone).
                param("alertUid","alert").
                param("request","request").
                param("revising","off")).
                andExpect(status().isOk());

        verify(cacheUtilManagerMock,times(1)).
                putUssdMenuForUser(testPhone,"livewire/" +
                        "contact/name" + "?alertUid=" + "alert" +
                        "&priorInput=" + "request" +  "&revising=1");




        mockMvc.perform(get("/ussd/livewire/contact/name").
                param(phoneInput,testPhone).
                param("alertUid","alert").
                param("request","request").
                param("priorInput","priorInput").
                param("revising","on")).
                andExpect(status().isOk());

        verify(cacheUtilManagerMock,times(1)).
                putUssdMenuForUser(testPhone,"livewire/" +
                        "contact/name" + "?alertUid=" + "alert" +
                        "&priorInput=" + "priorInput" + "&revising=1");

        verify(userManagementServiceMock,times(2)).
                findByInputNumber(testPhone);
    }
}


