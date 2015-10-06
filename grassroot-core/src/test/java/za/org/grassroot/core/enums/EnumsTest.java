package za.org.grassroot.core.enums;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;

import javax.transaction.Transactional;
import java.util.List;

import static org.junit.Assert.assertEquals;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class EnumsTest {



    @Test
    public void testRSVPEnum() {

        assertEquals("Yes",EventRSVPResponse.fromString("yes").toString());
        assertEquals("Yes",EventRSVPResponse.YES.toString());
        assertEquals("Maybe",EventRSVPResponse.MAYBE.toString());
        assertEquals("No",EventRSVPResponse.NO.toString());
        assertEquals("No",EventRSVPResponse.fromString("no").toString());
        assertEquals("No response yet",EventRSVPResponse.NO_RESPONSE.toString());
        assertEquals("Invalid RSVP",EventRSVPResponse.fromString("").toString());
        assertEquals("Invalid RSVP",EventRSVPResponse.fromString("any junk").toString());

    }

}
