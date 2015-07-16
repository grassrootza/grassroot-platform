package za.org.grassroot.meeting_organizer.service.repository;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.meeting_organizer.Application;
import za.org.grassroot.meeting_organizer.DbUnitConfig;
import za.org.grassroot.meeting_organizer.model.Group;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Application.class, DbUnitConfig.class})
@TestExecutionListeners( listeners = DbUnitTestExecutionListener.class, mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
@DatabaseSetup("/db/empty_tables.xml") // As with UserRepo test, init empty tables each time
public class GroupRepositoryIT {

    @Autowired
    GroupRepository groupRepository;

    @Test
    public void shouldSaveAndRetrieveGroupData() throws Exception {
        assertThat(groupRepository.count(), is(0L));

        Group groupToCreate = new Group();
        groupToCreate.setGroupName("TestGroup");
        assertNull(groupToCreate.getId());
        assertNull(groupToCreate.getCreatedDateTime());
        groupRepository.save(groupToCreate);

        assertThat(groupRepository.count(), is(1l));
        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertNotNull(groupFromDb.getCreatedDateTime());
        assertThat(groupFromDb.getGroupName(), is("TestGroup"));
    }

}
