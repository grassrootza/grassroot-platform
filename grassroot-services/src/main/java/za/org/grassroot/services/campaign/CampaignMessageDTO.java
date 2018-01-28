package za.org.grassroot.services.campaign;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import javax.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Getter @Setter @ApiModel("CampaignMessageDTO")
public class CampaignMessageDTO {

    @ApiModelProperty("An ID that enables the linking of messages to each other, locally generated to client")
    @NotNull(message = "campaign.message.id.required")
    private String messageId;
    @NotNull(message = "campaign.message.type.required")
    private CampaignActionType linkedActionType;
    @NotNull(message = "campaign.messages.required")
    private List<MessageLanguagePair> messages;

    // declaring using type instead of interface, because need to keep the ordering
    private List<String> nextMsgIds;

    private List<String> tags;
    // optionals, for restricting / adapting, in future
    private UserInterfaceType channel;
    private MessageVariationAssignment variation;
    private String joinWord;

    public List<Locale> getLanguages() {
        return messages.stream().map(MessageLanguagePair::getLanguage).distinct().collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "CampaignMessageDTO{" +
                "messageId='" + messageId + '\'' +
                ", linkedActionType=" + linkedActionType +
                ", messages=" + messages +
                ", nextMsgIds=" + nextMsgIds +
                '}';
    }
}
