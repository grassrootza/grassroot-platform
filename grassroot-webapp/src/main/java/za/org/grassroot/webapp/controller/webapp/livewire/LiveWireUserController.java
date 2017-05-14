package za.org.grassroot.webapp.controller.webapp.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.GeoLocationUtils;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.LiveWireAlertDTO;

import javax.servlet.http.HttpServletRequest;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luke on 2017/05/13.
 */
@Controller
@RequestMapping("/livewire/user")
@PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
public class LiveWireUserController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireUserController.class);
    private static final GeoLocation defaultLocation = new GeoLocation(-26.277636, 27.889045); // Freedom Square

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final GeoLocationBroker geoLocationBroker;

    @Autowired
    public LiveWireUserController(LiveWireAlertBroker liveWireAlertBroker, GeoLocationBroker geoLocationBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
        this.geoLocationBroker = geoLocationBroker;
    }

    @RequestMapping(value = {"/", ""}, method = RequestMethod.GET)
    public String liveWireUserIndex(Model model) {
        Page<LiveWireAlert> alerts = liveWireAlertBroker.loadAlerts(getUserProfile().getUid(),
                false, new PageRequest(0, 5, Sort.Direction.DESC, "creationTime"));
        model.addAttribute("alerts", alerts);
        model.addAttribute("canTag", liveWireAlertBroker.canUserTag(getUserProfile().getUid()));
        model.addAttribute("canRelease", liveWireAlertBroker.canUserRelease(getUserProfile().getUid()));
        return "livewire/user_home";
    }

    @RequestMapping(value = "/contacts", method = RequestMethod.GET)
    public String findLiveWireContacts(@RequestParam(required = false) Double longitude,
                                       @RequestParam(required = false) Double latitude,
                                       @RequestParam(required = false) Integer radius,
                                       Model model) {
        GeoLocation thisLocation;

        long startTime = System.currentTimeMillis();
        if (longitude != null && latitude != null) {
            thisLocation = new GeoLocation(latitude, longitude);
        } else {
            PreviousPeriodUserLocation priorLocation = geoLocationBroker.fetchUserLocation(getUserProfile().getUid());
            thisLocation = priorLocation != null ? priorLocation.getLocation() : defaultLocation;
        }

        int searchRadius = radius != null ? radius : GeoLocationUtils.DEFAULT_RADIUS;

        List<User> contacts = liveWireAlertBroker.fetchLiveWireContactsNearby(getUserProfile().getUid(),
                thisLocation, searchRadius);

        logger.info("Time to execute query: {} msecs, for records: {}", System.currentTimeMillis() - startTime,
                contacts.size());

        model.addAttribute("contacts", contacts);
        return "contact_list";
    }

    @RequestMapping(value = "/alert/list", method = RequestMethod.GET)
    public String displayLiveWireAlerts(@RequestParam Boolean onlyUnreviewed,
                                        @PageableDefault(page = 0, size = 5)
                                        @SortDefault.SortDefaults({
                                                @SortDefault(sort = "creationTime", direction = Sort.Direction.DESC),
                                                @SortDefault(sort = "type", direction = Sort.Direction.ASC)
                                        }) Pageable pageable, Model model) {

        Page<LiveWireAlert> alerts = liveWireAlertBroker.loadAlerts(getUserProfile().getUid(),
                onlyUnreviewed == null ? false : onlyUnreviewed,
                pageable);
        model.addAttribute("alerts", alerts.getContent()
                .stream().map(LiveWireAlertDTO::new).collect(Collectors.toList()));
        return "alert_list";
    }

    @RequestMapping(value = "/alert/details", method = RequestMethod.GET)
    @ResponseBody
    public LiveWireAlertDTO getAlertDetails(@RequestParam String alertUid) {
        return new LiveWireAlertDTO(liveWireAlertBroker.load(alertUid));
    }

    @RequestMapping(value = "/alert/tag", method = RequestMethod.POST)
    public String tagAlert(@RequestParam String alertUid,
                           @RequestParam String tags,
                           RedirectAttributes attributes, HttpServletRequest request) {
        List<String> tagList = Arrays.asList(tags.split(","));
        try {
            liveWireAlertBroker.addTagsToAlert(getUserProfile().getUid(), alertUid, tagList);
            addMessage(attributes, MessageType.SUCCESS, "livewire.tags.success", request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.tags.denied", request);
        } catch (InvalidParameterException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.tags.invalid", request);
        }
        return "redirect:/livewire/user/alert/list";
    }

    @RequestMapping(value = "/alert/release")
    public String releaseAlert(@RequestParam String alertUid,
                               @RequestParam(required = false) String tags,
                               RedirectAttributes attributes, HttpServletRequest request) {
        try {
            List<String> tagList = tags != null ? Arrays.asList(tags.split(",")) : null;
            liveWireAlertBroker.releaseAlert(getUserProfile().getUid(), alertUid, tagList);
            addMessage(attributes, MessageType.SUCCESS, "livewire.released.success", request);
        } catch (AccessDeniedException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.released.denied", request);
        } catch (IllegalArgumentException e) {
            addMessage(attributes, MessageType.ERROR, "livewire.released.error", request);
        }
        return "redirect:/livewire/user/alert/list";
    }
}
