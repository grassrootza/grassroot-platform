package za.org.grassroot.webapp.util;

import za.org.grassroot.core.domain.campaign.CampaignActionType;

import java.util.HashMap;
import java.util.Map;


public class USSDCampaignUtil {

    public static final String LANGUAGE_PARAMETER ="&lang=";
    public static final String CODE_PARAMETER ="&code=";
    public static final String TAG_PARAMETER ="&tag=";
    public static final String CAMPAIGN_PREFIX ="campaign.";
    public static final String TAG_ME_URL = "tag-me";
    public static final String JOIN_MASTER_GROUP_URL = "join-group";
    public static final String SIGN_PETITION_URL = "sign-petition";
    public static final String MORE_INFO_URL = "more-info";
    public static final String EXIT_URL = "exit";

    public static Map<CampaignActionType, String> getCampaignUrls() {
        Map<CampaignActionType, String> campaignUrlMap = new HashMap<>();
        campaignUrlMap.put(CampaignActionType.TAG_ME, TAG_ME_URL);
        campaignUrlMap.put(CampaignActionType.JOIN_MASTER_GROUP, JOIN_MASTER_GROUP_URL);
        campaignUrlMap.put(CampaignActionType.SIGN_PETITION, SIGN_PETITION_URL);
        campaignUrlMap.put(CampaignActionType.MORE_INFO,MORE_INFO_URL);
        campaignUrlMap.put(CampaignActionType.EXIT, EXIT_URL);
        return campaignUrlMap;
    }



}
