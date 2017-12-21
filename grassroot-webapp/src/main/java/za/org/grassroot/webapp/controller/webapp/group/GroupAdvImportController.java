package za.org.grassroot.webapp.controller.webapp.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.integration.DataImportBroker;
import za.org.grassroot.services.exception.GroupSizeLimitExceededException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.ExcelSheetAnalysis;
import za.org.grassroot.webapp.model.web.GroupWrapper;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

/**
 * Created by luke on 2016/10/19.
 */
@Controller
@RequestMapping("/group/import/")
@SessionAttributes({ "groupWrapper" })
public class GroupAdvImportController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(GroupAdvImportController.class);

    private final DataImportBroker dataImportBroker;
    private final GroupBroker groupBroker;

    @Autowired
    public GroupAdvImportController(DataImportBroker dataImportBroker, GroupBroker groupBroker) {
        this.dataImportBroker = dataImportBroker;
        this.groupBroker = groupBroker;
    }

    @ModelAttribute("groupWrapper")
    public GroupWrapper getGroupWrapper() {
        return new GroupWrapper();
    }

    @GetMapping("/start")
    public String importGroupFromFile(Model model, @RequestParam String groupUid) {
        model.addAttribute("groupUid", groupUid);

        Group group = groupBroker.load(groupUid);
        User user = userManagementService.load(getUserProfile().getUid());

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);

        model.addAttribute("groupUid", groupUid);
        return "group/bulk_import_extra";
    }

    @RequestMapping(value = "analyze", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ExcelSheetAnalysis analyzeExcelFile(@ModelAttribute MultipartFile file) {
        logger.info("Analyzing file ... with name = " + file.getName());
        try {
            File tempStore = File.createTempFile("mbrs", "xls");
            tempStore.deleteOnExit();
            file.transferTo(tempStore);
            logger.info("File stored: {}", tempStore.getAbsolutePath());
            return new ExcelSheetAnalysis(tempStore.getAbsolutePath(), dataImportBroker.extractFirstRowOfCells(tempStore));
        } catch (IOException e) {
            return new ExcelSheetAnalysis(null, null);
        }
    }

    @PostMapping(value = "confirm")
    public String confirmMembers(Model model, @RequestParam String groupUid, @RequestParam String tempPath, @RequestParam Integer phoneColumn,
                                 @RequestParam Integer nameColumn, @RequestParam Integer roleColumn, @RequestParam Boolean header) {
        File tmpFile = new File(tempPath);
        logger.info("phoneCol = {}, nameCol = {}, header = {}, loaded temp file, path = {}", phoneColumn, nameColumn, header, tmpFile.getAbsolutePath());

        List<MembershipInfo> members = dataImportBroker.processMembers(tmpFile, header, phoneColumn, nameColumn,
                roleColumn == -1 ? null : roleColumn, null, null);
        model.addAttribute("members", members);
        model.addAttribute("groupUid", groupUid);

        GroupWrapper groupWrapper = new GroupWrapper();
        groupWrapper.setListOfMembers(members);

        model.addAttribute("groupUid", groupUid);
        model.addAttribute("groupWrapper", groupWrapper);

        return "group/bulk_import_confirm";
    }

    @PostMapping(value = "done")
    public String addMembers(@RequestParam String groupUid, @ModelAttribute GroupWrapper groupWrapper,
                             RedirectAttributes attributes, HttpServletRequest request) {
        // todo : intercept prior to here (UI)
        try {
            groupBroker.addMembers(getUserProfile().getUid(), groupUid, new HashSet<>(groupWrapper.getListOfMembers()),
                    GroupJoinMethod.BULK_IMPORT, false);
            addMessage(attributes, MessageType.SUCCESS, "group.bulk.success", new Integer[]{groupWrapper.getListOfMembers().size()}, request);
        } catch (GroupSizeLimitExceededException e) {
            addMessage(attributes, MessageType.ERROR, "group.addmember.limit", request);
        }
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }

}
