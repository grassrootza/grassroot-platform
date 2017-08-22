package za.org.grassroot.webapp.controller.webapp.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.livewire.LiveWireSendingBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.LiveWireAlertDTO;

import javax.servlet.http.HttpServletRequest;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by luke on 2017/05/13.
 */
@Controller
@RequestMapping("/livewire/alert")
@PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
@PropertySource(value = "${grassroot.integration.properties}", ignoreResourceNotFound = true)
public class LiveWireAlertReviewController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireAlertReviewController.class);

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final LiveWireSendingBroker liveWireSendingBroker;
    private final DataSubscriberBroker dataSubscriberBroker;

    @Autowired
    public LiveWireAlertReviewController(LiveWireAlertBroker liveWireAlertBroker, LiveWireSendingBroker liveWireSendingBroker, DataSubscriberBroker dataSubscriberBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
        this.liveWireSendingBroker = liveWireSendingBroker;
        this.dataSubscriberBroker = dataSubscriberBroker;
    }

    @RequestMapping(value = {"/", ""}, method = RequestMethod.GET)
    public String liveWireUserIndex(Model model) {
        populateModel(new PageRequest(0, 5, Sort.Direction.DESC, "creationTime"), false, model);
        return "livewire/alerts";
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public String displayLiveWireAlerts(@RequestParam(required = false) Boolean onlyUnreviewed,
                                        @PageableDefault(page = 0, size = 5)
                                        @SortDefault.SortDefaults({
                                                @SortDefault(sort = "creationTime", direction = Sort.Direction.DESC),
                                                @SortDefault(sort = "type", direction = Sort.Direction.ASC)
                                        }) Pageable pageable, Model model) {
        populateModel(pageable, onlyUnreviewed == null ? false : onlyUnreviewed, model);
        return "livewire/alerts";
    }

    private void populateModel(Pageable pageable, boolean unreviewedOnly, Model model) {
        Page<LiveWireAlert> alerts = liveWireAlertBroker.loadAlerts(getUserProfile().getUid(),
                unreviewedOnly, pageable);
        model.addAttribute("alerts", alerts);
        model.addAttribute("pageable", pageable);
        model.addAttribute("sort", pageable.getSort().iterator().next());
        model.addAttribute("canTag", liveWireAlertBroker.canUserTag(getUserProfile().getUid()));
        boolean canRelease = liveWireAlertBroker.canUserRelease(getUserProfile().getUid());
        model.addAttribute("canRelease", canRelease);
        if (canRelease) {
            model.addAttribute("publicLists", dataSubscriberBroker.listPublicSubscribers());
        }
    }

    @RequestMapping(value = "details", method = RequestMethod.GET)
    @ResponseBody
    public LiveWireAlertDTO getAlertDetails(@RequestParam String alertUid) {
        return new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid));
    }

    @RequestMapping(value = "tag", method = RequestMethod.POST)
    public String tagAlert(@RequestParam String alertUid,
                           @RequestParam String tags,
                           RedirectAttributes attributes, HttpServletRequest request) {
        List<String> tagList = Arrays.asList(tags.split("\\s*,\\s*"));
        try {
            liveWireAlertBroker.setTagsForAlert(getUserProfile().getUid(), alertUid, tagList);
            addMessage(attributes, MessageType.SUCCESS, "livewire.tags.success", request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.tags.denied", request);
        } catch (InvalidParameterException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.tags.invalid", request);
        }
        return "redirect:/livewire/alert/list";
    }

    @RequestMapping(value = "review", method = RequestMethod.POST)
    public String releaseAlert(@RequestParam String alertUid,
                               @RequestParam boolean send,
                               @RequestParam(required = false) String publicLists,
                               @RequestParam(required = false) String tags,
                               RedirectAttributes attributes, HttpServletRequest request) {
        try {
            List<String> tagList = tags != null ? Arrays.asList(tags.split(",")) : null;
            List<String> listOfLists = publicLists != null ? Arrays.asList(publicLists.split(",")) : null;
            logger.debug("publicLists: {}, tags received: {}", listOfLists, tagList);
            liveWireAlertBroker.reviewAlert(getUserProfile().getUid(), alertUid, tagList, send, listOfLists);
            if (send) {
                liveWireSendingBroker.sendLiveWireAlerts(Collections.singleton(alertUid));
            }
            addMessage(attributes, MessageType.SUCCESS, send ?
                    "livewire.released.success" : "livewire.blocked.done", request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.released.denied", request);
        } catch (IllegalArgumentException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.released.error", request);
        }
        return "redirect:/livewire/alert/list";
    }
}
