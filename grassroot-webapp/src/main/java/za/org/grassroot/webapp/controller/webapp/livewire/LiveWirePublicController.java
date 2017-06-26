package za.org.grassroot.webapp.controller.webapp.livewire;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.webapp.controller.BaseController;

/**
 * Created by luke on 2017/06/20.
 */
@Controller
@RequestMapping("/livewire/public/")
public class LiveWirePublicController extends BaseController {

    private final DataSubscriberBroker dataSubscriberBroker;

    @Autowired
    public LiveWirePublicController(DataSubscriberBroker dataSubscriberBroker) {
        this.dataSubscriberBroker = dataSubscriberBroker;
    }

    @RequestMapping(value = "/info", method = RequestMethod.GET)
    public void viewLiveWireInfo(@RequestParam String emailAddress) {

    }

    @RequestMapping(value = "/unsubscribe", method = RequestMethod.GET)
    public void unsubscribePrompt(@RequestParam String emailAddress) {

    }

    @RequestMapping(value = "/unsubscribe/confirm", method = RequestMethod.GET)
    public void unsubscribeDo(@RequestParam String emailAddress) {

    }

}