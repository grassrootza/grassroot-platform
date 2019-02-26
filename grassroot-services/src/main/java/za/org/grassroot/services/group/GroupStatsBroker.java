package za.org.grassroot.services.group;


import javax.annotation.Nullable;
import java.util.Map;

public interface GroupStatsBroker {

    Map<String, Integer> getMembershipGrowthStats(String groupUid, Integer year, @Nullable Integer month);

    Map<String, Integer> getProvincesStats(String groupUid);

    Map<String, Integer> getSourcesStats(String groupUid);

    Map<String, Integer> getOrganisationsStats(String groupUid);

    Map<String, Integer> getMemberDetailsStats(String groupUid);

    Map<String, Integer> getTopicInterestStatsPercentage(String groupUid);

    Map<String, Integer> getTopicInterestStatsRaw(String groupUid, Boolean clearCache);

}
