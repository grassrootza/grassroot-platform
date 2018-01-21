package za.org.grassroot.services.group;


import javax.annotation.Nullable;
import java.util.Map;

public interface GroupStatsBroker {

    Map<String, Integer> getMembershipGrowthStats(String groupUid, Integer year, @Nullable Integer month);

    Map<String, Long> getProvincesStats(String groupUid);

    Map<String, Long> getSourcesStats(String groupUid);

    Map<String, Long> getOrganisationsStats(String groupUid);

    Map<String, Integer> getMemberDetailsStats(String groupUid);

    Map<String, Integer> getTopicInterestStats(String groupUid);

}
