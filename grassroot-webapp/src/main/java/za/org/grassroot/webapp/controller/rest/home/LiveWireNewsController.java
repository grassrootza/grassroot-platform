package za.org.grassroot.webapp.controller.rest.home;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

@Slf4j @RestController
@Grassroot2RestController
@RequestMapping("/v2/api/news") @Api("/v2/api/news")
public class LiveWireNewsController {

    private final LiveWireAlertBroker liveWireAlertBroker;

    @Autowired
    public LiveWireNewsController(LiveWireAlertBroker liveWireAlertBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
    }

    @RequestMapping(value = "/list/headlines", method = RequestMethod.GET)
    public Page<PublicLiveWireDTO> fetchLiveWireNewsHeadlines(Pageable pageable) {
        return liveWireAlertBroker.fetchReleasedAlerts(pageable).map(alert -> new PublicLiveWireDTO(alert, false, false));
    }

    @RequestMapping(value = "/list/full", method = RequestMethod.GET)
    public Page<PublicLiveWireDTO> fetchLiveWireNewsArticles(Pageable pageable) {
        return liveWireAlertBroker.fetchReleasedAlerts(pageable).map(alert -> new PublicLiveWireDTO(alert, true, false));
    }

    @RequestMapping(value = "/page/number",method = RequestMethod.GET)
    public ResponseEntity<Integer> findAlertpageNumber(@RequestParam String alertUid){
        int page = 0;
        Pageable pageable = PageRequest.of(page, 10, Sort.Direction.DESC,"creationTime");
        boolean found = false;

        Page<LiveWireAlert> liveWireAlerts = liveWireAlertBroker.fetchReleasedAlerts(pageable);
        for(int x = 0;x < liveWireAlerts.getTotalPages();x++){
            for (LiveWireAlert alert:liveWireAlerts.getContent()) {
                if(alert.getUid().equals(alertUid)){
                    found = true;
                    page = pageable.getPageNumber();
                }
            }
            if(found){
                break;
            }else{
                page++;
                pageable = PageRequest.of(page,10, Sort.Direction.DESC,"creationTime");
                liveWireAlerts = liveWireAlertBroker.fetchReleasedAlerts(pageable);
            }
        }
        return ResponseEntity.ok(page);
    }


}
