package za.org.grassroot.core.enums;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;

import static org.junit.Assert.assertEquals;


@Slf4j @RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = TestContextConfiguration.class)
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
