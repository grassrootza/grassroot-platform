package za.org.grassroot.services.group;

import javax.annotation.Nullable;
import java.util.Map;

public interface GroupStatsBroker {

    Map<String, Integer> getMembershipGrowthStats(String groupUid, Integer year, @Nullable Integer month);

}
