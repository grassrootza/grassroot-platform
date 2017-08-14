package za.org.grassroot.webapp.controller.webapp.livewire;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import za.org.grassroot.core.dto.LiveWireContactDTO;
import za.org.grassroot.services.livewire.LiveWireContactBroker;
import za.org.grassroot.webapp.controller.BaseController;

import java.util.List;

@Controller
@RequestMapping("/livewire/contacts")
@PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
public class LiveWireContactController extends BaseController {

    private final LiveWireContactBroker liveWireContactBroker;

    @Autowired
    public LiveWireContactController(LiveWireContactBroker liveWireContactBroker) {
        this.liveWireContactBroker = liveWireContactBroker;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public @ResponseBody
    List<LiveWireContactDTO> liveWireContactsList() {
        return liveWireContactBroker.loadLiveWireContacts(getUserProfile().getUid());
    }

}
