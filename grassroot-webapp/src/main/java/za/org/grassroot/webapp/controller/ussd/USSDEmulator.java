package za.org.grassroot.webapp.controller.ussd;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class USSDEmulator {


    @RequestMapping(value = "/ussd-emulator")
    public String index() {
        return "/ussd-emulator/emulator";
    }

    @RequestMapping(value = "/ussd-emulator/start-emulator")
    @ResponseBody
    public Request startEmulator(String input) {

        return new Request();
    }

    @RequestMapping(value = "/ussd-emulator/process-request")
    @ResponseBody
    public Request process(String input) {

        return new Request();
    }
}
