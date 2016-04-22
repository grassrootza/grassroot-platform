package za.org.grassroot.language;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 
 * @author Joe Stelmach
 */
public class RecurrenceTest extends AbstractTest {
  @BeforeClass
  public static void oneTime() {
    Locale.setDefault(Locale.getDefault());
    TimeZone.setDefault(TimeZone.getTimeZone("Africa/Johannesburg"));
    initCalendarAndParser();
  }
 
  @Test
  public void testRelative() throws Exception {
    Date reference = sdfWithTime.parse("3/3/2011 12:00 am");
    calendarSource = new CalendarSource(reference);
    
    DateGroup group = _parser.parse("every friday until two tuesdays from now", reference).get(0);
    Assert.assertEquals(1, group.getDates().size());
    validateDate(group.getDates().get(0), 4, 3, 2011);
    Assert.assertTrue(group.isRecurring());
    validateDate(group.getRecursUntil(), 15, 3, 2011);
    
    group = _parser.parse("every saturday or sunday", reference).get(0);
    Assert.assertEquals(2, group.getDates().size());
    validateDate(group.getDates().get(0), 5, 3, 2011);
    validateDate(group.getDates().get(1), 6, 3, 2011);
    Assert.assertTrue(group.isRecurring());
    Assert.assertNull(group.getRecursUntil());
  }
}
