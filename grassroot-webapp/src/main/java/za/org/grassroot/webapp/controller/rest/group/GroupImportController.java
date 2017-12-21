package za.org.grassroot.webapp.controller.rest.group;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.integration.DataImportBroker;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.model.web.ExcelSheetAnalysis;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Grassroot2RestController
@Api("/api/group/import") @Slf4j
@RequestMapping(value = "/api/group/import")
public class GroupImportController extends GroupBaseController {

    private final DataImportBroker dataBroker;

    public GroupImportController(JwtService jwtService, UserManagementService userManagementService, DataImportBroker dataBroker) {
        super(jwtService, userManagementService);
        this.dataBroker = dataBroker;
    }

    @RequestMapping(value = "/analyze", method = RequestMethod.POST)
    public ResponseEntity<ExcelSheetAnalysis> analyzeExcelFile(@ModelAttribute MultipartFile file) throws IOException {
        File tempStore = File.createTempFile("members", "xls");
        tempStore.deleteOnExit();
        file.transferTo(tempStore);
        return ResponseEntity.ok(new ExcelSheetAnalysis(tempStore.getAbsolutePath(), dataBroker.extractFirstRowOfCells(tempStore)));
    }

    @RequestMapping(value = "/confirm", method = RequestMethod.POST)
    public ResponseEntity<List<AddMemberInfo>> confirmMembers(@RequestParam String tempPath,
                                                               @RequestParam Integer phoneColumn,
                                                               @RequestParam Integer nameColumn,
                                                               @RequestParam Integer roleColumn,
                                                               @RequestParam Boolean header) {
        File tmpFile = new File(tempPath);
        log.info("phoneCol = {}, nameCol = {}, header = {}, loaded temp file, path = {}", phoneColumn, nameColumn, header, tmpFile.getAbsolutePath());

        return ResponseEntity.ok(dataBroker
                .processMembers(tmpFile, phoneColumn, nameColumn, roleColumn == -1 ? null : roleColumn, header)
                .stream().map(AddMemberInfo::new).collect(Collectors.toList()));
    }

}
