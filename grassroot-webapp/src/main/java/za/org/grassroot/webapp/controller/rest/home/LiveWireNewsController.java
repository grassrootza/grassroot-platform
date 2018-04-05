package za.org.grassroot.webapp.controller.rest.home;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.List;

@Slf4j @RestController
@Grassroot2RestController
@RequestMapping("/api/news")
public class LiveWireNewsController {

    private final LiveWireAlertBroker liveWireAlertBroker;

    @Autowired
    public LiveWireNewsController(LiveWireAlertBroker liveWireAlertBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
    }

    @RequestMapping(value = "/list/headlines", method = RequestMethod.GET)
    public Page<PublicLiveWireDTO> fetchLiveWireNewsHeadlines(Pageable pageable) {
        return liveWireAlertBroker.findPublicAlerts(pageable).map(alert -> new PublicLiveWireDTO(alert, false));
    }

    @RequestMapping(value = "/list/full", method = RequestMethod.GET)
    public Page<PublicLiveWireDTO> fetchLiveWireNewsArticles(Pageable pageable) {
        return liveWireAlertBroker.findPublicAlerts(pageable).map(alert -> new PublicLiveWireDTO(alert, true));
    }

    @RequestMapping(value = "/page/number",method = RequestMethod.GET)
    public ResponseEntity<Integer> findAlertpageNumber(@RequestParam String alertUid){
        int page = 0;
        Pageable pageable = new PageRequest(page,10, Sort.Direction.DESC,"creationTime");
        boolean found = false;

        Page<LiveWireAlert> liveWireAlerts = liveWireAlertBroker.findPublicAlerts(pageable);
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
                pageable = new PageRequest(page,10, Sort.Direction.DESC,"creationTime");
                liveWireAlerts = liveWireAlertBroker.findPublicAlerts(pageable);
            }
        }
        return ResponseEntity.ok(page);
    }
}
