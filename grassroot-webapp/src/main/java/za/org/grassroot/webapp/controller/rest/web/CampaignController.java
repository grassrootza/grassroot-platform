package za.org.grassroot.webapp.controller.rest.web;


import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.services.campaign.CampaignBroker;

@RestController
@RequestMapping("/web/")
public class CampaignController {

    private final CampaignBroker campaignBroker;


    public CampaignController(CampaignBroker campaignBroker) {
        this.campaignBroker = campaignBroker;
    }


}
