package za.org.grassroot.webapp.controller.ussd;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


/**
 * Created by luke on 2015/09/08.
 */
public class USSDGroupControllerIT extends USSDAbstractIT {

    private Logger log = LoggerFactory.getLogger(USSDGroupControllerIT.class);

    @Autowired
    UserManagementService userManager;

    @Autowired
    GroupManagementService groupManager;

    @Autowired
    GroupRepository groupRepository;

    private final String groupPath = "group/";
    private final String groupParam = "groupId";
    private Group testGroup;


    // todo: restore these tests once have figured out how to get it working with roles & permissions (authorities issue)

    @Before
    public void setUp() throws Exception {
        base.port(port);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalizeWhitespace(true);
        createTestUser();
        // createGroup();

    }

    @Test
    public void createGroupTokenShouldWork() throws Exception {

        /* final URI createGroupToken = testPhoneUri(groupPath + "/token-do").queryParam(groupParam, testGroup.getId()).
                queryParam("days", 1).build().toUri();

        ResponseEntity<String> tokenResponse = executeQuery(createGroupToken);
        log.info("Response menu on token creation: " + tokenResponse.getBody());
        Group reloadedGroup = groupManager.loadGroup(testGroup.getId());

        log.info("Group for which we want token: " + reloadedGroup.getId());
        assertNotNull(testGroup);
        assertNotNull(reloadedGroup);
        assertThat(testGroup.getId(), is(reloadedGroup.getId()));
        assertThat(testGroup.getCreatedByUser().getId(), is(reloadedGroup.getCreatedByUser().getId()));
        String code = reloadedGroup.getGroupTokenCode();
        assertNotNull(code);
        assertTrue(groupManager.tokenExists(code));
        assertNotNull(groupManager.getGroupByToken(code));*/



    }

    @Test
    public void createGroupWithNameShouldWork() throws Exception {

        // todo: this is throwing errors at present because of the authentication issues on roles .. to restore once that fixed for tests overall

        /* final URI createGroup = testPhoneUri(groupPath + "/create-do").queryParam(USSDUrlUtil.userInputParam, "TAC")
                .build().toUri();
        executeQuery(createGroup);
        User sessionUser = userManager.findByInputNumber(testPhone);
        Group group= groupManager.getLastCreatedGroup(sessionUser);
        assertThat(group.getGroupName(),is("TAC"));;
        groupRepository.delete(group.getId());*/

    }

    @Test
    public void createGroupAddNumbersShouldWork() throws Exception{

        // this is throwing errors at present because of the authentication issues on roles .. to restore once that fixed for tests overall

        /* final URI addUsers = testPhoneUri(groupPath + "add-numbers-do").queryParam(groupParam,testGroup.getId()).queryParam(USSDUrlUtil.userInputParam, "0616780986")
                .build().toUri();
        executeQuery(addUsers);
        User addedUser = userManager.findByInputNumber("27616780986");
        assertNotNull(addedUser);*/


    }
    @Test
    public void renameGroupShouldWork() throws Exception{

        // todo: as above, restore this when have permissions & authorities worked out

        /* final URI createGroup = testPhoneUri(groupPath + "/create-do").queryParam(USSDUrlUtil.userInputParam, "TAC")
                .build().toUri();
        executeQuery(createGroup);
        User sessionUser = userManager.findByInputNumber(testPhone);
        Group group= groupManager.getLastCreatedGroup(sessionUser);
        final URI renameGroup = testPhoneUri(groupPath + "/rename-do").queryParam(groupParam,group.getId())
                .queryParam(USSDUrlUtil.userInputParam, "Treatment Action Campaign")
                .queryParam("newgroup", String.valueOf(false))
                .build().toUri();
        executeQuery(renameGroup);
        assertEquals("Treatment Action Campaign",groupManager.getGroupName(group.getId()));
        groupRepository.delete(group.getId());*/

    }

  /*  @Test
    public void unsubscibeDoShouldWork() throws Exception{

        final URI createGroup = testPhoneUri(groupPath + "/create-do").queryParam(USSDUrlUtil.userInputParam, "TAC")
                .build().toUri();
        executeQuery(createGroup);

        final URI addUsers = testPhoneUri(groupPath + "add-numbers-do").queryParam(groupParam,testGroup.getId()).queryParam(USSDUrlUtil.userInputParam, "0799814669")
                .build().toUri();
        executeQuery(addUsers);
        User testUser = userManager.findByInputNumber("27799814669", null);
        final URI removeUser = testPhoneUri(groupPath + "unsubscribe-do").queryParam(groupParam,testGroup.getId()).queryParam(USSDUrlUtil.userInputParam, testUser.getPhoneNumber())
                .build().toUri();
        executeQuery(removeUser);

    }*/

    private Group createGroup() {

        // todo: as above, restore this when have permissions & authorities worked out

        final URI createGroupUri = testPhoneUri(groupPath + "/create-do").queryParam(freeTextParam, String.join(" ", testPhones)).
                build().toUri();
        executeQueries(Arrays.asList(createGroupUri));
        User sessionUser = userManager.findByInputNumber(testPhone);
        testGroup = groupManager.getLastCreatedGroup(sessionUser);
        return testGroup;

    }

    private void deleteAllGroups(){

        List<Group> userGroups= groupManager.getAllGroups();
        for(Group group: userGroups){
            groupRepository.delete(group.getId());
        }
    }




}
