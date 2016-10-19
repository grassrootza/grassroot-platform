package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.integration.DataImportBroker;
import za.org.grassroot.webapp.controller.BaseController;

/**
 * Created by luke on 2016/10/19.
 */
@Controller
@RequestMapping("/group/paid/")
public class PaidGroupController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(PaidGroupController.class);

    @Autowired
    DataImportBroker dataImportBroker;

    @GetMapping("import")
    public String importGroupFromFile(Model model) {
        return "/group/bulk_import_extra";
    }

    @PostMapping("upload/excel")
    public @ResponseBody String handleFileUpload(@RequestParam MultipartFile file, RedirectAttributes redirectAttributes) {
        dataImportBroker.importExcelFile(file, null, null, null);
        return "Hello New World";
    }

}
