package za.org.grassroot.core.enums;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;

import javax.transaction.Transactional;

import static org.junit.Assert.assertEquals;


@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
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
