package za.org.grassroot.webapp.util;

import za.org.grassroot.core.domain.campaign.CampaignActionType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class USSDCampaignUtil {

    public static final String LANGUAGE_PARAMETER ="&lang=";
    public static final String CAMPAIGN_ID_PARAMETER ="&campaignUid=";
    public static final String CAMPAIGN_PREFIX ="campaign.";
    public static final String TAG_ME_URL = "tag-me";
    public static final String JOIN_MASTER_GROUP_URL = "join-group";
    public static final String SIGN_PETITION_URL = "sign-petition";
    public static final String MORE_INFO_URL = "more-info";
    public static final String SHARE_URL = "share";
    public static final String EXIT_URL = "exit";
    public static final String MESSAGE_UID = "messageUid";
    public static final String MESSAGE_UID_PARAMETER = "messageUid=";
    public static final String SET_LANGUAGE_URL ="set-lang";

    private static Map<CampaignActionType, String> campaignUrlMap;

    static {
        Map<CampaignActionType,String> urls = new HashMap<>();
        urls.put(CampaignActionType.TAG_ME, TAG_ME_URL);
        urls.put(CampaignActionType.JOIN_MASTER_GROUP, JOIN_MASTER_GROUP_URL);
        urls.put(CampaignActionType.SIGN_PETITION, SIGN_PETITION_URL);
        urls.put(CampaignActionType.MORE_INFO,MORE_INFO_URL);
        urls.put(CampaignActionType.EXIT_NEGATIVE, EXIT_URL);
        urls.put(CampaignActionType.SHARE, SHARE_URL);
        campaignUrlMap = Collections.unmodifiableMap(urls);
    }

    public static Map<CampaignActionType, String> getCampaignUrlPrefixs() {
        return campaignUrlMap;
    }
}
