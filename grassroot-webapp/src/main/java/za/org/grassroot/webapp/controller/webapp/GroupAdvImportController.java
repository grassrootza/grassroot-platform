package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.integration.DataImportBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.ExcelSheetAnalysis;

import java.util.Arrays;

/**
 * Created by luke on 2016/10/19.
 */
@Controller
@RequestMapping("/group/paid/import/")
@SessionAttributes("file")
public class GroupAdvImportController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(GroupAdvImportController.class);

    @Autowired
    DataImportBroker dataImportBroker;

    @GetMapping("/")
    public String importGroupFromFile(Model model) {
        return "/group/bulk_import_extra";
    }

    @PostMapping(value = "excel")
    public @ResponseBody String handleFileUpload(@RequestParam MultipartFile file, RedirectAttributes redirectAttributes) {
        dataImportBroker.importExcelFile(file, null, null, null);
        return "Hello New World";
    }

    // todo : okay, now add the methods for checking the headers etc
    @RequestMapping(value = "analyze", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody
    ExcelSheetAnalysis analyzeExcelFile(@RequestParam MultipartFile file) {
        logger.info("Analyzing file ...");
        return new ExcelSheetAnalysis(dataImportBroker.extractFirstRowOfCells(file));
    }

}
