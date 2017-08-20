package za.org.grassroot.webapp.controller.webapp.livewire;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.webapp.controller.BaseController;

@Controller
@RequestMapping("/livewire/alert")
public class LiveWireAlertCreateController extends BaseController {

    @RequestMapping("/create")
    public String createAlertForm() {
        return "livewire/create";
    }

}
