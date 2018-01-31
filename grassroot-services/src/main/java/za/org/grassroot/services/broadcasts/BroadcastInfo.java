package za.org.grassroot.services.broadcasts;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.integration.socialmedia.ManagedPage;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter @Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BroadcastInfo {

    boolean isSmsAllowed;
    int smsCostCents;

    boolean isFbConnected;
    List<ManagedPage> facebookPages;

    boolean isTwitterConnected;
    ManagedPage twitterAccount;

    List<String> joinLinks;
    List<String> campaignLinks;

    // we will use these for constructing the various counts (once also have various counting infra set up)
    long allMemberCount;

    Map<String, Set<String>> taskTeamMemberUids;
    Map<Province, Set<String>> provinceMemberUids;
    Map<String, Set<String>> topicMemberUids;

    @Override
    public String toString() {
        return "BroadcastInfo{" +
                "isSmsAllowed=" + isSmsAllowed +
                ", smsCostCents=" + smsCostCents +
                ", isFbConnected=" + isFbConnected +
                ", facebookPages=" + facebookPages +
                ", isTwitterConnected=" + isTwitterConnected +
                ", twitterAccount=" + twitterAccount +
                ", allMemberCount=" + allMemberCount +
                '}';
    }
}
