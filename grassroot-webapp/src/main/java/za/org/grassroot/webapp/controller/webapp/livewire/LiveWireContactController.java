package za.org.grassroot.webapp.controller.webapp.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.dto.LiveWireContactDTO;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.GeoLocationUtils;
import za.org.grassroot.services.livewire.LiveWireContactBroker;
import za.org.grassroot.webapp.controller.BaseController;

import java.util.List;

@Controller
@RequestMapping("/livewire/contact")
@PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
public class LiveWireContactController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireContactController.class);

    @Value("${grassroot.mapzen.api.key:mapzen-key}")
    private String mapZenApiKey;

    private static final GeoLocation defaultLocation = new GeoLocation(-26.277636, 27.889045); // Freedom Square

    private final LiveWireContactBroker liveWireContactBroker;
    private final GeoLocationBroker geoLocationBroker;

    @Autowired
    public LiveWireContactController(LiveWireContactBroker liveWireContactBroker, GeoLocationBroker geoLocationBroker) {
        this.liveWireContactBroker = liveWireContactBroker;
        this.geoLocationBroker = geoLocationBroker;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public String liveWireContactsList() {
        return "livewire/contacts";
    }

    @RequestMapping(value = "/fetch", method = RequestMethod.GET)
    public @ResponseBody
    List<LiveWireContactDTO> liveWireContactsListJSON() {
        return liveWireContactBroker.loadLiveWireContacts(getUserProfile().getUid());
    }

    @RequestMapping(value = "/map", method = RequestMethod.GET)
    public String liveWireContactsMap(@RequestParam(required = false) Double longitude,
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

        List<LiveWireContactDTO> contacts = liveWireContactBroker
                .fetchLiveWireContactsNearby(getUserProfile().getUid(), thisLocation, searchRadius);

        logger.info("Time to execute query: {} msecs, for records: {}", System.currentTimeMillis() - startTime,
                contacts.size());

        model.addAttribute("location", thisLocation);
        model.addAttribute("radius", searchRadius);
        model.addAttribute("contacts", contacts);
        model.addAttribute("mapzenKey", mapZenApiKey);
        return "livewire/contacts_map";
    }

    @RequestMapping(value = "contacts/pull", method = RequestMethod.GET)
    public @ResponseBody List<LiveWireContactDTO> updateLiveWireContacts(@RequestParam double longitude,
                                                                         @RequestParam double latitude,
                                                                         @RequestParam int radius) {
        return liveWireContactBroker
                .fetchLiveWireContactsNearby(getUserProfile().getUid(), new GeoLocation(latitude, longitude), radius);
    }

}
