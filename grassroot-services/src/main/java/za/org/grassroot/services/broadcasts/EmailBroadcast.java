package za.org.grassroot.services.broadcasts;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.dto.GrassrootEmail;

import java.util.List;

@Getter @Setter @Builder
public class EmailBroadcast {

    private String subject;
    private String content;
    private String imageUid;
    private String fromName;
    private String fromAddress;
    private DeliveryRoute deliveryRoute;
    private List<String> attachmentFileRecordUids;

    public void setFromFieldsIfEmpty(User fromUser) {
        if (StringUtils.isEmpty(fromName)) {
            this.fromName = fromUser.getName();
        }
        if (StringUtils.isEmpty(fromAddress)) {
            this.fromAddress = fromUser.getEmailAddress();
        }
    }

    public GrassrootEmail toGrassrootEmail() {
        return new GrassrootEmail.EmailBuilder()
                .from(fromName)
                .fromAddress(fromAddress)
                .subject(subject)
                .content(content)
                .htmlContent(content)
                .attachmentRecordUids(attachmentFileRecordUids)
                .build();
    }

}
