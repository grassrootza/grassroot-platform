package za.org.grassroot.webapp.controller.rest.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

@RestController
@RequestMapping(value = "/api/campaign/manage")
public class CampaignManagerController {

    private static final Logger logger = LoggerFactory.getLogger(CampaignManagerController.class);

    private final CampaignBroker campaignBroker;

    @Autowired
    public CampaignManagerController(CampaignBroker campaignBroker) {
        this.campaignBroker = campaignBroker;
    }

    @RequestMapping(value = "/list/{userUid}")
    public ResponseEntity<ResponseWrapper> fetchCampaignsManagedByUser(@RequestParam String userUid) {
        return RestUtil.okayResponseWithData(RestMessage.USER_ACTIVITIES,
                campaignBroker.getCampaignsCreatedByUser(userUid));
    }
}
