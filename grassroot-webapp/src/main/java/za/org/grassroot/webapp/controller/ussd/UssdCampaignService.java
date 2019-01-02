package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.campaign.CampaignMessage;

public interface UssdCampaignService {
	CampaignMessage tagMembership(String inputNumber, String messageUid, String parentMsgUid);
}
