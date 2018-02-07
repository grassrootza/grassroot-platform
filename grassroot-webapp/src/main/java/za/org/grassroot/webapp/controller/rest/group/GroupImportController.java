package za.org.grassroot.webapp.controller.rest.group;

import com.amazonaws.util.IOUtils;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.integration.data.DataImportBroker;
import za.org.grassroot.integration.data.MemberImportResult;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.model.web.ExcelSheetAnalysis;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

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
    public ResponseEntity<MemberImportResult> confirmMembers(@RequestParam String tempPath,
                                                             @RequestParam(required = false) Integer nameColumn,
                                                             @RequestParam(required = false) Integer phoneColumn,
                                                             @RequestParam(required = false) Integer emailColumn,
                                                             @RequestParam(required = false) Integer provinceColumn,
                                                             @RequestParam(required = false) Integer roleColumn,
                                                             @RequestParam(required = false) Integer firstNameColumn,
                                                             @RequestParam(required = false) Integer surnameColumn,
                                                             @RequestParam(required = false) Integer affiliationColumn,
                                                             @RequestParam boolean header) {
        File tmpFile = new File(tempPath);
        log.info("phoneCol = {}, nameCol = {}, header = {}, loaded temp file, path = {}", phoneColumn, nameColumn, header, tmpFile.getAbsolutePath());

        Integer nameC = nameColumn == -1 ? null : nameColumn;
        Integer phoneC = phoneColumn == -1 ? null : phoneColumn;
        Integer emailC = emailColumn == -1 ? null : emailColumn;
        Integer provinceC = provinceColumn == -1 ? null : provinceColumn;
        Integer roleC = roleColumn == -1 ? null : roleColumn;
        Integer firstC = firstNameColumn == -1 ? null : firstNameColumn;
        Integer surnameC = surnameColumn == -1 ? null : surnameColumn;
        Integer affilC = affiliationColumn == -1 ? null : affiliationColumn;

        return ResponseEntity.ok(dataBroker.processMembers(tmpFile,
                header, phoneC, nameC, roleC, emailC, provinceC, firstC, surnameC, affilC));
    }

    @RequestMapping(value = "/errors/xls")
    public ResponseEntity<byte[]> getErrorSheet(@RequestParam String errorFilePath, HttpServletResponse response)
            throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add("Content-Disposition", "attachment; filename=\"import-errors.xls\"");
        headers.add("Cache-Control", "no-cache");
        headers.add("Pragma", "no-cache");
        headers.setDate("Expires", 0);

        File errorFile = new File(errorFilePath);
        byte[] data = IOUtils.toByteArray(new FileInputStream(errorFile));
        headers.setContentDispositionFormData("import-errors.xls", "import-errors.xls");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

}
