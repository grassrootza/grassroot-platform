package za.org.grassroot.core.enums;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;

import javax.transaction.Transactional;

import static org.junit.Assert.assertEquals;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class EnumsTest {



    @Test
    public void testRSVPEnum() {

        assertEquals(EventRSVPResponse.YES,EventRSVPResponse.fromString("yes"));
        assertEquals(EventRSVPResponse.NO,EventRSVPResponse.fromString("no"));
        assertEquals(EventRSVPResponse.MAYBE, EventRSVPResponse.fromString("maybe"));
        assertEquals(EventRSVPResponse.MAYBE, EventRSVPResponse.fromString("abstain"));
        assertEquals(EventRSVPResponse.INVALID_RESPONSE,EventRSVPResponse.fromString(""));
        assertEquals(EventRSVPResponse.INVALID_RESPONSE,EventRSVPResponse.fromString("any junk"));

    }

}
