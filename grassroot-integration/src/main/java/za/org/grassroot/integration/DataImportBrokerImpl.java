package za.org.grassroot.integration;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.dto.MembershipInfo;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2016/10/19.
 */
@Service
public class DataImportBrokerImpl implements DataImportBroker {

    private static final Logger logger = LoggerFactory.getLogger(DataImportBrokerImpl.class);

    private DataFormatter dataFormatter;
    private FormulaEvaluator formulaEvaluator;

    @PostConstruct
    private void init() {
        dataFormatter = new DataFormatter();
    }

    @Override
    public List<String> extractFirstRowOfCells(File file) {
        List<String> firstRow = new ArrayList<>();
        try {
            Workbook wb = WorkbookFactory.create(file);
            formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
            Row row = wb.getSheetAt(0).getRow(0);
            for (Cell cell : row) {
                firstRow.add(dataFormatter.formatCellValue(cell, formulaEvaluator));
            }
        } catch (IOException e) {
            logger.info("Error, file input stream corrupted");
        } catch (InvalidFormatException e) {
            logger.info("Error, invalid file format");
        }
        return firstRow;
    }

    @Override
    public List<MembershipInfo> processMembers(File file, Integer phoneColumn, Integer nameColumn, Integer roleColumn, boolean headerRow) {
        List<MembershipInfo> importedMembers = new ArrayList<>();
        try {
            Workbook wb = WorkbookFactory.create(file);
            formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = wb.getSheetAt(0);
            int nameRef = nameColumn == null ? 0 : nameColumn;
            int phoneRef = phoneColumn == null ? 1 : phoneColumn;

            for (int i = headerRow ? 1 : 0; i <  sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                MembershipInfo member = memberFromRow(row, nameRef, phoneRef, roleColumn);
                importedMembers.add(member);
                logger.debug("Read in member: {}", member);
            }
            logger.info("Read in file!");
        } catch (IOException e) {
            logger.info("Error! File not found: " + e.toString());
        } catch (InvalidFormatException e) {
            logger.info("Error! Could not read file: " + e.toString());
        }
        return importedMembers;
    }

    @Override
    public List<String> extractFirstColumnOfSheet(File file) {
        List<String> firstColumn = new ArrayList<>();
        try {
            Workbook wb = WorkbookFactory.create(file);
            formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 0; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                firstColumn.add(dataFormatter.formatCellValue(row.getCell(0), formulaEvaluator));
            };
        } catch (IOException e) {
            logger.info("Error, file input stream corrupted");
        } catch (InvalidFormatException e) {
            logger.info("Error, invalid file format");
        }
        return firstColumn;
    }

    private MembershipInfo memberFromRow(Row row, int nameCol, int phoneCol, Integer roleCol) {
        String roleName = roleCol != null ? convertRoleName(row.getCell(roleCol).getStringCellValue())
                : BaseRoles.ROLE_ORDINARY_MEMBER;

        return new MembershipInfo(dataFormatter.formatCellValue(row.getCell(phoneCol), formulaEvaluator), roleName,
                dataFormatter.formatCellValue(row.getCell(nameCol), formulaEvaluator));
    }

    private String convertRoleName(final String cellValue) {
        // todo : use a more sophisticated form of parsing (bring in Selo?)
        if (cellValue.toLowerCase().contains("organizer")) {
            return BaseRoles.ROLE_GROUP_ORGANIZER;
        }
        if (cellValue.toLowerCase().contains("committee")) {
            return BaseRoles.ROLE_COMMITTEE_MEMBER;
        }
        return BaseRoles.ROLE_ORDINARY_MEMBER;
    }

}
