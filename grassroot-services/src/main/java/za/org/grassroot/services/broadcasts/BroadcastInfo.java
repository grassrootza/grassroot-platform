package za.org.grassroot.services.broadcasts;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import za.org.grassroot.core.enums.Province;

import java.util.Map;
import java.util.Set;

@Getter @Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BroadcastInfo {

    boolean isSmsAllowed;
    int smsCostCents;

    boolean isFbConnected;
    Map<String, String> facebookPages;

    boolean isTwitterConnected;
    String twitterAccountName;

    // we will use these for constructing the various counts (once also have various counting infra set up)
    Map<String, Set<String>> taskTeamMemberUids;
    Map<Province, Set<String>> provinceMemberUids;
    Map<String, Set<String>> topicMemberUids;

}
