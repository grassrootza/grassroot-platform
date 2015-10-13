package za.org.grassroot.core.acl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;

import javax.sql.DataSource;
import javax.transaction.Transactional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class AclDatabaseConfigurationTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @Before
    public void setup() {
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
    }

    @Test
    public void testIfExistsMemberTable() {
        Integer count = jdbcTemplate.queryForObject("select count(id) from acl_entry", Integer.class);
        assertThat(count, is(0));
    }

    @Test
    public void testIfAclClassesExists() throws Exception {

        String className = jdbcTemplate.queryForObject("select class from acl_class where class = '" + Group.class.getCanonicalName() + "'", String.class);
        assertThat(className, equalTo(Group.class.getCanonicalName()));


        className = jdbcTemplate.queryForObject("select class from acl_class where class = '" + Event.class.getCanonicalName() + "'", String.class);
        assertThat(className, equalTo(Event.class.getCanonicalName()));
    }

    @Test
    public void testMaskBits() throws Exception {

        assertThat(1 << 4, is(equalTo(16)));

    }
}
