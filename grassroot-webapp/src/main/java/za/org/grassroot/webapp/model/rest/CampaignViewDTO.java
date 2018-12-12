package za.org.grassroot.webapp.model.rest;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.dto.CampaignLogsDataCollection;
import za.org.grassroot.services.campaign.CampaignMessageDTO;
import za.org.grassroot.services.campaign.MessageLanguagePair;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Getter @Slf4j @ToString
public class CampaignViewDTO {

    private boolean fullInfo;

    private String campaignUid;
    private String name;
    private String description;
    private String creatingUserName;
    private String creatingUserUid;
    private Instant createdDate;
    private String campaignCode;
    private Instant campaignStartDate;
    private Instant campaignEndDate;
    private CampaignType campaignType;

    private String campaignUrl;
    private boolean petitionConnected;
    private String petitionUrl;

    private String masterGroupUid;
    private String masterGroupName;

    private long totalJoined;
    private long totalEngaged;
    private long totalSigned;
    private long lastActivityEpochMilli;

    private String textJoinWord;
    private List<String> joinTopics;
    private List<CampaignMessageDTO> campaignMessages;

    private boolean outboundSmsEnabled;
    private long outboundSmsLimit;
    private long outboundSmsSpent;
    private long outboundSmsUnitCost;

    private String campaignImageKey;

    private Locale defaultLanguage;

    private CampaignViewDTO(String campaignUid, String campaignName, String campaignDescription, CampaignType campaignType,
                           String createUserUid, String createUserName, Instant createdDateTime,
                           Instant campaignStartDate, Instant campaignEndDate, String campaignUrl, String campaignCode, List<String> campaignTags){
        this.campaignCode = campaignCode;
        this.name = campaignName;
        this.description = campaignDescription;
        this.campaignType = campaignType;
        this.creatingUserName = createUserName;
        this.creatingUserUid = createUserUid;
        this.createdDate = createdDateTime;
        this.campaignStartDate = campaignStartDate;
        this.campaignEndDate = campaignEndDate;
        this.campaignUid = campaignUid;
        this.campaignUrl = campaignUrl;
        this.joinTopics = campaignTags;
    }

    public CampaignViewDTO(Campaign campaign, CampaignLogsDataCollection logsDataCollection, boolean fullInfo) {
        this(campaign.getUid(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getCampaignType(),
                campaign.getCreatedByUser().getUid(),
                campaign.getCreatedByUser().getName(),
                campaign.getCreatedDateTime(),
                campaign.getStartDateTime(),
                campaign.getEndDateTime(),
                campaign.getLandingUrl(),
                campaign.getCampaignCode(),
                campaign.getJoinTopics());

        this.fullInfo = fullInfo;

        if (logsDataCollection != null) {
            this.totalEngaged = logsDataCollection.getTotalEngaged();
            this.totalJoined = logsDataCollection.getTotalJoined();
            this.totalSigned = logsDataCollection.getTotalSigned();
            this.lastActivityEpochMilli = logsDataCollection.getLastActivityEpochMilli();
        }

        log.debug("Assembling DTO, full info? : {}", fullInfo);
        if (fullInfo) {
            this.masterGroupName = campaign.getMasterGroup() != null ? campaign.getMasterGroup().getGroupName() : null;
            this.masterGroupUid = campaign.getMasterGroup() != null ? campaign.getMasterGroup().getUid() : null;

            log.debug("Petition connected? : {}", campaign.getPetitionApiUrl());
            this.petitionConnected = !StringUtils.isEmpty(campaign.getPetitionApiUrl());
            if (this.petitionConnected) {
                this.petitionUrl = campaign.getPetitionApiUrl();
            }

            this.outboundSmsEnabled = campaign.isOutboundTextEnabled();
            this.outboundSmsLimit = campaign.getOutboundBudget() / campaign.getAccount().getFreeFormCost();
            this.outboundSmsSpent = campaign.getOutboundSpent();
            this.outboundSmsUnitCost = campaign.getAccount().getFreeFormCost();
            log.debug("campaign with sms unit cost: {}", this.outboundSmsUnitCost);

            if (!campaign.getCampaignMessages().isEmpty()) {
                this.campaignMessages = groupCampaignMessages(campaign);
                log.debug("campaign DTO message list: {}", campaign.getCampaignMessages());
            }

            if (campaign.getCampaignImage() != null) {
                this.campaignImageKey = campaign.getCampaignImage().getUid();
            }

            if (!StringUtils.isEmpty(campaign.getPublicJoinWord())) {
                this.textJoinWord = campaign.getPublicJoinWord();
            }

            this.defaultLanguage = campaign.getDefaultLanguage();
        }
    }

    private List<CampaignMessageDTO> groupCampaignMessages(Campaign campaign) {
        List<String> messageGroupIds = campaign.getCampaignMessages().stream().map(CampaignMessage::getMessageGroupId)
                .distinct().collect(Collectors.toList());
        MultiValueMap<String, Locale> msgLocales = new LinkedMultiValueMap<>();
        Map<String, CampaignMessage> mappedMsgs = new HashMap<>();

        campaign.getCampaignMessages().forEach(cm -> {
            msgLocales.add(cm.getMessageGroupId(), cm.getLocale());
            mappedMsgs.put(cm.getMessageGroupId() + "__" + cm.getLocale(), cm);
        });

        log.info("message locales: {}", msgLocales);

        return messageGroupIds.stream().filter(msgLocales::containsKey).map(msgId -> {
            CampaignMessageDTO cmDto = new CampaignMessageDTO();
            cmDto.setMessageId(msgId);
            Locale refLocale = msgLocales.get(msgId).get(0);
            CampaignMessage refMsg = mappedMsgs.get(msgId + "__" + refLocale);

            cmDto.setLinkedActionType(refMsg.getActionType());
            cmDto.setChannel(refMsg.getChannel());
            cmDto.setTags(refMsg.getTagList());
            cmDto.setVariation(refMsg.getVariation());

            cmDto.setMessages(msgLocales.get(msgId).stream().map(locale -> new MessageLanguagePair(locale, mappedMsgs.get(msgId + "__" + locale).getMessage()))
                    .collect(Collectors.toList()));

            return cmDto;
        }).collect(Collectors.toList());
    }


}
