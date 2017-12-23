package za.org.grassroot.core.dto;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.util.List;

@Getter @Setter
public class BroadcastDTO {

    private int smsCount;
    private int emailCount;
    private String fbPage;
    private String twitterAccount;

    private Instant dateTimeSent;

    private float costEstimate;

    private String smsContent;
    private String emailContent;
    private String fbPost;
    private String twitterPost;

    private List<Province> provinces;
    private List<String> topics;

    public BroadcastDTO(Broadcast broadcast) {
        // set things up
    }

}
