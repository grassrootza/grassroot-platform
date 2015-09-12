package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class GroupRepositoryTest {

    private Logger log = Logger.getLogger(getClass().getName());


    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository; // LSJ: there may be a less expensive way to do this?

    @Test
    public void shouldSaveAndRetrieveGroupData() throws Exception {

        assertThat(groupRepository.count(), is(0L));

        Group groupToCreate = new Group();

        User userToDoTests = new User();
        userToDoTests.setPhoneNumber("56789");
        userRepository.save(userToDoTests);

        groupToCreate.setGroupName("TestGroup");
        groupToCreate.setCreatedByUser(userToDoTests);
        assertNull(groupToCreate.getId());
        assertNull(groupToCreate.getCreatedDateTime());
        groupRepository.save(groupToCreate);

        assertThat(groupRepository.count(), is(1l));
        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertNotNull(groupFromDb.getCreatedDateTime());
        assertThat(groupFromDb.getGroupName(), is("TestGroup"));
        assertThat(groupFromDb.getCreatedByUser().getPhoneNumber(), is("56789"));
    }

    @Test
    public void shouldSaveAndFindByCreatedUser() throws Exception {

        Group groupToCreate = new Group();
        User userToDoTests = new User();
        userToDoTests.setPhoneNumber("100001");
        userRepository.save(userToDoTests);
        groupToCreate.setGroupName("TestGroup");
        groupToCreate.setCreatedByUser(userToDoTests);
        assertNull(groupToCreate.getId());
        assertNull(groupToCreate.getCreatedDateTime());
        groupRepository.save(groupToCreate);
        Group groupFromDb = groupRepository.findByCreatedByUser(userToDoTests).iterator().next();
        log.info(groupFromDb.toString());
        assertNotNull(groupFromDb);
        assertEquals(userToDoTests.getId(), groupFromDb.getCreatedByUser().getId());
    }

    @Test
    public void shouldFindLastCreatedGroupForUser() throws Exception {

        User userToDoTests = new User();
        userToDoTests.setPhoneNumber("100002");
        userRepository.save(userToDoTests);
        Group group1 = new Group();
        group1.setGroupName("TestGroup1");
        group1.setCreatedByUser(userToDoTests);
        groupRepository.save(group1);
        Group group2 = new Group();
        group2.setGroupName("TestGroup2");
        group2.setCreatedByUser(userToDoTests);
        Group savedGroup2 = groupRepository.save(group2);
        Group groupFromDb = groupRepository.findFirstByCreatedByUserOrderByIdDesc(userToDoTests);
        log.finest("latest group........." + groupFromDb.toString());
        assertEquals(savedGroup2.getId(), groupFromDb.getId());
    }

    @Test
    public void shouldSaveParentRelationship() {
        User user = userRepository.save(new User("1111111111"));
        Group ga = groupRepository.save(new Group("ga", user));
        Group ga1 = groupRepository.save(new Group("ga1", user, ga));
        assertEquals(ga.getId(), ga1.getParent().getId());

    }

    @Test
    public void shouldReturnLevel1Children() {
        User user = userRepository.save(new User("2222222222"));
        Group gb = groupRepository.save(new Group("gb", user));
        Group gb1 = groupRepository.save(new Group("gb1", user, gb));
        Group gb2 = groupRepository.save(new Group("gb2", user, gb));
        List<Group> children = groupRepository.findByParent(gb);
        assertEquals(2,children.size());
        for (Group child : children) {
            log.finest("child......" + child.toString());
        }
    }

    //TODO the tree query, might have to change it to save children rather than parent
    // if we want to stick to JPA as that seems to be the only way that JPA can fetch
    // the tree, dont think I should do a native query as that ties us down to a database

    @Test
    public void shouldReturnAllChildren() {
        User user = userRepository.save(new User("3333333333"));
        Group gc = groupRepository.save(new Group("gc", user));
        Group gc1 = groupRepository.save(new Group("gc1", user, gc));
        Group gc2 = groupRepository.save(new Group("gc2", user, gc));
        Group gc1a = groupRepository.save(new Group("gc1a", user, gc1));
        Group gc1b = groupRepository.save(new Group("gc1b", user, gc1));
        //fail("must still do the query");
//        List<Group> children = groupRepository.findByParent(gc);
//        assertEquals(2,children.size());
//        for (Group child : children) {
//            log.finest("child......" + child.toString());
//        }
    }

}


