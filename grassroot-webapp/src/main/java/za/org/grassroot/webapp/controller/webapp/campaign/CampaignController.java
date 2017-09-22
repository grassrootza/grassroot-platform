package za.org.grassroot.webapp.controller.webapp.campaign;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.CampaignMessageWrapper;
import za.org.grassroot.webapp.model.web.CampaignWrapper;
import za.org.grassroot.webapp.model.web.VoteWrapper;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/campaign/")
@SessionAttributes({"campaignCreator"})
public class CampaignController extends BaseController{

    private static final Logger log = LoggerFactory.getLogger(CampaignController.class);
    private static final String CREATE_CAMPAIGN_VIEW = "campaign/create";
    private static final String VIEW_CAMPAIGN_VIEW = "campaign/create";
    private static final String ADD_CAMPAIGN_TAG_VIEW = "campaign/add/tag";
    private static final String ADD_CAMPAIGN_MESSAGE_VIEW = "campaign/add/message";
    private final static String SA_TIME_ZONE = "Africa/Johannesburg";
    private final static String CAMPAIGN_MODEL_ATTRIBUTE = "campaign";

    private final CampaignBroker campaignBroker;
    private final UserManagementService userManager;

    @Autowired
    public CampaignController(CampaignBroker campaignBroker, UserManagementService userManager) {
        this.campaignBroker = campaignBroker;
        this.userManager = userManager;
    }

    @RequestMapping("create")
    public String createCampaign(Model model, @ModelAttribute("campaignCreator") @Validated CampaignWrapper campaignWrapper,
                                 BindingResult bindingResult, HttpServletRequest request){
        if (bindingResult.hasErrors()) {
            addMessage(model, BaseController.MessageType.ERROR, "create.campaign.request.invalid", request);
            model.addAttribute(CAMPAIGN_MODEL_ATTRIBUTE, campaignWrapper);
            return CREATE_CAMPAIGN_VIEW;
        }
        List<String> tagList = null;
        if(campaignWrapper.getTags() != null && !campaignWrapper.getTags().isEmpty()){
            tagList = Collections.list(Collections.enumeration(campaignWrapper.getTags()));
        }
        User user = userManager.load(campaignWrapper.getUserUid());
        LocalDate firstDate = LocalDate.parse(campaignWrapper.getStartDate());
        Instant campaignStartDate = firstDate.atStartOfDay(ZoneId.of(SA_TIME_ZONE)).toInstant();

        LocalDate secondDate = LocalDate.parse(campaignWrapper.getEndDate());
        Instant campaignEndDate = secondDate.atStartOfDay(ZoneId.of(SA_TIME_ZONE)).toInstant();

        Campaign campaign = campaignBroker.createCampaign(campaignWrapper.getName(),campaignWrapper.getCode(),campaignWrapper.getDescription(),user,campaignStartDate, campaignEndDate, tagList);
        model.addAttribute(CAMPAIGN_MODEL_ATTRIBUTE,campaign);
        return CREATE_CAMPAIGN_VIEW;
    }

    @RequestMapping("add/tag")
    public String addCampaignTag(Model model, @RequestParam(value="campaignCode", required = true) String campaignCode,
                                 @RequestParam(value="tag", required = true) String tag,
                                 RedirectAttributes attributes, HttpServletRequest request){
        List<String> tagList = new ArrayList<>();
        tagList.add(tag);
        Campaign campaign = campaignBroker.addCampaignTags(campaignCode,tagList);
        model.addAttribute(CAMPAIGN_MODEL_ATTRIBUTE,campaign);
        return ADD_CAMPAIGN_TAG_VIEW;
    }


    @RequestMapping("add/message")
    public String addCampaignMessage(Model model, @ModelAttribute("messageCreator") @Validated CampaignMessageWrapper messageWrapper,
                                     BindingResult bindingResult, HttpServletRequest request){
        if (bindingResult.hasErrors()) {
            addMessage(model, BaseController.MessageType.ERROR, "create.campaign.request.invalid", request);
            model.addAttribute(CAMPAIGN_MODEL_ATTRIBUTE, messageWrapper);
            return CREATE_CAMPAIGN_VIEW;
        }
        List<String> tagList = null;
        if(messageWrapper.getTags() != null && !messageWrapper.getTags().isEmpty()){
            tagList = Collections.list(Collections.enumeration(messageWrapper.getTags()));
        }
        User user = userManager.load(messageWrapper.getUserUid());
        Campaign campaign = campaignBroker.addCampaignMessage(messageWrapper.getCampaignCode(),messageWrapper.getMessage(),
                Locale.forLanguageTag(messageWrapper.getLanguage()),null,messageWrapper.getChannel(), user, tagList);
        model.addAttribute(CAMPAIGN_MODEL_ATTRIBUTE,campaign);
        return ADD_CAMPAIGN_MESSAGE_VIEW;
    }

    @RequestMapping("view")
    public String viewCampaign(Model model, @ModelAttribute("vote") VoteWrapper vote, BindingResult bindingResult,
                               @RequestParam(value = "selectedGroupUid", required = false) String selectedGroupUid,
                               HttpServletRequest request, RedirectAttributes redirectAttributes){

        return VIEW_CAMPAIGN_VIEW;

    }
}
