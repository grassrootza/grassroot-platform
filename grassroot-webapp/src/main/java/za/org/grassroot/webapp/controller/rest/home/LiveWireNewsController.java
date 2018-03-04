package za.org.grassroot.webapp.controller.rest.home;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

@Slf4j @RestController
@Grassroot2RestController
@RequestMapping("/news")
public class LiveWireNewsController {

    private final LiveWireAlertBroker liveWireAlertBroker;

    @Autowired
    public LiveWireNewsController(LiveWireAlertBroker liveWireAlertBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public Page<PublicLiveWireDTO> fetchLiveWireNewsArticles(Pageable pageable) {
        return liveWireAlertBroker.findPublicAlerts(pageable).map(PublicLiveWireDTO::new);
    }
}
