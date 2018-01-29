package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.webapp.controller.webapp.group.GroupCreateController;
import za.org.grassroot.webapp.model.web.GroupWrapper;

import java.util.HashSet;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Created by paballo on 2016/01/19.
 */
public class GroupCreateControllerTest extends WebAppAbstractUnitTest {

    // private static final Logger logger = LoggerFactory.getLogger(MeetingControllerTest.class);

    @Mock
    private AccountGroupBroker accountGroupBrokerMock;

    @InjectMocks
    private GroupCreateController groupCreateController;

    @Before
    public void setUp() {
        setUp(groupCreateController);
    }

    @Test
    public void startGroupIndexWorksWithoutParentId() throws Exception {

        GroupWrapper dummyGroupCreator = new GroupWrapper();
        dummyGroupCreator.setGroupName("DummyGroup");
        dummyGroupCreator.addMember(new MembershipInfo(sessionTestUser, BaseRoles.ROLE_GROUP_ORGANIZER, null, null));
        mockMvc.perform(get("/group/create"))
                .andExpect(view().name("group/create"))
                .andExpect(model().attribute("groupCreator", hasProperty("addedMembers", hasSize(dummyGroupCreator.getAddedMembers().size()))));
    }

    @Test
    public void startGroupIndexWorksWithParentId() throws Exception {
        Group dummyGroup = Group.makeEmpty();

        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        mockMvc.perform(get("/group/create").param("parent", dummyGroup.getUid()))
                .andExpect(view().name("group/create"))
                .andExpect(model().attribute("groupCreator", hasProperty("parent", is(dummyGroup))));
        verify(groupBrokerMock, times(1)).load(dummyGroup.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void createGroupWorks() throws Exception {

        GroupWrapper dummyGroupCreator = new GroupWrapper();
        dummyGroupCreator.setGroupName("DummyGroup");
        MembershipInfo organizer = new MembershipInfo(sessionTestUser.getPhoneNumber(),
                                                      BaseRoles.ROLE_GROUP_ORGANIZER, sessionTestUser.getDisplayName());
        dummyGroupCreator.addMember(organizer);
        Group dummyGroup = new Group(dummyGroupCreator.getGroupName(), sessionTestUser);
        dummyGroup.addMember(sessionTestUser, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);

        when(groupBrokerMock.create(sessionTestUser.getUid(), dummyGroupCreator.getGroupName(), null,
                                    new HashSet<>(dummyGroupCreator.getAddedMembers()),
                                    GroupPermissionTemplate.DEFAULT_GROUP, null, (60 * 24), true, false)).thenReturn(dummyGroup);

        when((userManagementServiceMock.findByInputNumber(sessionTestUser.getPhoneNumber()))).thenReturn(sessionTestUser);

        mockMvc.perform(post("/group/create").sessionAttr("groupCreator", dummyGroupCreator).
                param("groupTemplate", GroupPermissionTemplate.DEFAULT_GROUP.toString()))
                .andExpect(view().name("redirect:view"))
                .andExpect(model().attribute("groupUid", dummyGroup.getUid()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("view?groupUid=" + dummyGroup.getUid()));

        verifyNoMoreInteractions(userManagementServiceMock);
    }

    /*
    todo : move these into better testing of the central method (since these are now replaced with client side validation
    and error handling in the create method)
    
    @Test
    public void addMemberWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        mockMvc.perform(post("/group/create").param("addMember", "").sessionAttr("groupCreator", groupCreator))
                .andExpect(status().isOk())
                .andExpect(model().attribute("groupCreator", instanceOf(GroupWrapper.class)))
                .andExpect(view().name("group/create"));
    }

    @Test
    public void addMemberFailsValidation() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        User user = new User("100001");
        groupCreator.addMember(new MembershipInfo(user));
        mockMvc.perform(post("/group/create").param("addMember", "")
                .sessionAttr("groupCreator", groupCreator))
                .andExpect(status().isOk())
                .andExpect(view().name("group/create"));

    }

    @Test
    public void removeMemberWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(new MembershipInfo("100001", null, ""));
        mockMvc.perform(post("/group/create").param("removeMember", String.valueOf(0)).param("removeMember", "")
                .sessionAttr("groupCreator", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/create"));
    }*/

}
