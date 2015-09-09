package za.org.grassroot.webapp.controller.ussd;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupTokenCode;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.GroupTokenService;
import za.org.grassroot.webapp.GrassRootWebApplicationConfig;

import javax.transaction.Transactional;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;



/**
 * Created by luke on 2015/09/08.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {GrassRootWebApplicationConfig.class})
@WebIntegrationTest(randomPort = true)
@Transactional
public class USSDGroupControllerTest extends USSDControllerTest {

    private Logger log = LoggerFactory.getLogger(USSDGroupControllerTest.class);

    private final String groupPath = "group/";
    private final String groupParam = "groupId";

    @Autowired
    GroupTokenService groupTokenService;

    @Before
    public void setUp() throws Exception {
        base.port(port);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalizeWhitespace(true);
    }

    @Test
    public void createGroupToken() throws Exception {

        Group createdGroup = utilCreateGroup();

        final URI createGroupToken = testPhoneUri(groupPath + "/token-do").queryParam(groupParam, createdGroup.getId()).
                queryParam("days", 1).build().toUri();

        ResponseEntity<String> tokenResponse = executeQuery(createGroupToken);

        log.info("Response menu on token creation: " + tokenResponse.getBody());

        Group reloadedGroup = groupManager.loadGroup(createdGroup.getId());

        log.info("Group for which we want token: " + reloadedGroup.toString());

        assertNotNull(createdGroup);
        assertNotNull(reloadedGroup);
        assertThat(createdGroup.getId(), is(reloadedGroup.getId()));
        assertThat(createdGroup.getCreatedByUser(), is(reloadedGroup.getCreatedByUser()));

        // todo: fix whatever is making the below not fail

        // GroupTokenCode tokenCode1 = groupTokenService.getTokenFromGroup(reloadedGroup);
        // GroupTokenCode tokenCode2 = reloadedGroup.getGroupTokenCode();

        // assertNotNull(tokenCode1);
        // assertNotNull(tokenCode2);

        // log.info("Token code found for group: " + tokenCode1.getCode());

    }


    private Group utilCreateGroup() {

        final URI createUserUri = testPhoneUri("start").build().toUri();
        final URI createGroupUri = testPhoneUri(groupPath + "/create-do").queryParam(freeTextParam, String.join(" ", testPhones)).
                build().toUri();

        List<ResponseEntity<String>> responseEntities = executeQueries(Arrays.asList(createUserUri, createGroupUri));

        User sessionUser = userManager.findByInputNumber(testPhone);
        Group groupToReturn = groupManager.getLastCreatedGroup(sessionUser);

        return groupToReturn;

    }

}
