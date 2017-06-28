package za.org.grassroot.webapp.controller.webapp.livewire;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;

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
    public String viewLiveWireInfo(Model model, @RequestParam(required = false) String email) {
        model.addAttribute("includeUnsubscribe", false);
        model.addAttribute("emailAddress", email);
        return "livewire/info_public";
    }

    @RequestMapping(value = "/unsubscribe", method = RequestMethod.GET)
    public String unsubscribePrompt(Model model, @RequestParam String email) {
        model.addAttribute("includeUnsubscribe", true);
        model.addAttribute("emailAddress",  email);
        return "livewire/info_public";
    }

    // note: at some stage probably want to add a confirmation & update step
    @RequestMapping(value = "/unsubscribe/confirm", method = RequestMethod.POST)
    public String unsubscribeDo(@RequestParam String emailAddress,
                              Model model, HttpServletRequest request) {
        dataSubscriberBroker.removeEmailFromAllSubscribers(emailAddress);
        addMessage(model, MessageType.SUCCESS, "livewire.email.unsubscribed", request);
        return "livewire/unsubscribed";
    }

}