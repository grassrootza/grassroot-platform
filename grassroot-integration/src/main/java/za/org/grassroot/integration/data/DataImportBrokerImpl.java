package za.org.grassroot.integration.data;

import org.apache.commons.validator.routines.EmailValidator;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by luke on 2016/10/19.
 */
@Service
public class DataImportBrokerImpl implements DataImportBroker {

    private static final Logger logger = LoggerFactory.getLogger(DataImportBrokerImpl.class);

    private static final String EMPTY_PLACEHOLDER = "[blank]";

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
            int lastColumn = row.getLastCellNum();
            for (int cn = 0; cn < lastColumn; cn++) {
                Cell cell = row.getCell(cn, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                firstRow.add(extractHeader(cell));
            }
        } catch (IOException e) {
            logger.info("Error, file input stream corrupted");
        } catch (InvalidFormatException e) {
            logger.info("Error, invalid file format");
        }
        logger.info("extracted first row of cells, look like: {}", firstRow);
        return firstRow;
    }

    private String extractHeader(Cell cell) {
        final String header = dataFormatter.formatCellValue(cell, formulaEvaluator);
        return StringUtils.isEmpty(header) ? EMPTY_PLACEHOLDER : header;
    }

    @Override
    public MemberImportResult processMembers(File file, boolean headerRow, Integer phoneColumn, Integer nameColumn, Integer roleColumn, Integer emailCol, Integer provinceCol, Integer firstNameCol, Integer surnameCol, Integer afiilCol) {
        logger.info("file path name : {}, path: {}, abs path: {}", file.getName(), file.getPath(), file.getAbsolutePath());

        MemberImportResult result = new MemberImportResult();
        List<MembershipInfo> importedMembers = new ArrayList<>();
        List<Row> failedRows = new ArrayList<>();
        try {
            Workbook wb = WorkbookFactory.create(file);
            formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = wb.getSheetAt(0);

            for (int i = headerRow ? 1 : 0; i <  sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                if (!checkRowEmpty(row)) {
                    try {
                        MembershipInfo member = memberFromRow(row, nameColumn, phoneColumn, emailCol, provinceCol, roleColumn,
                                firstNameCol, surnameCol, afiilCol);
                        importedMembers.add(member);
                        logger.debug("Read in member: {}", member);
                    } catch (Exception e) {
                        logger.info("failed to parse row, adding to error sheet, error headline: {}", e.getMessage());
                        failedRows.add(row);
                    }
                }
            }

            if (!failedRows.isEmpty()) {
                try {
                    List<String[]> failedRowValues = new ArrayList<>();
                    File errorFile = createErrorFile(headerRow ? sheet.getRow(0) : null,
                            failedRows, failedRowValues, file.getName());
                    result.setErrorFilePath(errorFile.getPath());
                    result.setErrorRows(failedRowValues);
                } catch(Exception e) {
                    logger.error("something went wrong creating error file", e);
                }
            }

        } catch (IOException e) {
            logger.info("Error! File not found: " + e.toString());
        } catch (InvalidFormatException e) {
            logger.info("Error! Could not read file: " + e.toString());
        }

        result.setProcessedMembers(importedMembers);
        return result;
    }

    private File createErrorFile(Row headerRow, List<Row> errorRows, List<String[]> valueStore, String fileName)
            throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("ErrorRows");
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);

        int headerOffset = headerRow != null ? 1 : 0;
        if (headerRow != null) {
            Row newHeaderRow = sheet.createRow(0);
            valueStore.add(copyRow(workbook, headerRow, newHeaderRow));
        }

        for (int rowIndex  = 0; rowIndex < errorRows.size(); rowIndex++) {
            Row errorRow = errorRows.get(rowIndex);
            Row newRow = sheet.createRow(rowIndex + headerOffset);
            valueStore.add(copyRow(workbook, errorRow, newRow));
        }

        File outputFile = File.createTempFile(fileName + "-errors", ".xls");
        FileOutputStream fos = new FileOutputStream(outputFile);
        workbook.write(fos);
        fos.flush();
        fos.close();
        return outputFile;

    }

    private String[] copyRow(XSSFWorkbook workbook, Row errorRow, Row newRow) {
        String[] values = new String[errorRow.getLastCellNum()];
        for (int colIndex = 0; colIndex < errorRow.getLastCellNum(); colIndex++) {
            Cell sourceCell = errorRow.getCell(colIndex);
            Cell newCell = newRow.createCell(colIndex);
            if (sourceCell == null) {
                newCell = null;
                continue;
            }

            XSSFCellStyle newCellStyle = workbook.createCellStyle();
            newCellStyle.cloneStyleFrom(sourceCell.getCellStyle());
            newCell.setCellStyle(newCellStyle);
            newCell.setCellType(sourceCell.getCellTypeEnum());

            // Set the cell data value
            switch (sourceCell.getCellTypeEnum()) {
                case BLANK:
                    newCell.setCellValue(sourceCell.getStringCellValue());
                    values[colIndex] = sourceCell.getStringCellValue();
                    break;
                case BOOLEAN:
                    newCell.setCellValue(sourceCell.getBooleanCellValue());
                    values[colIndex] = String.valueOf(sourceCell.getBooleanCellValue());
                    break;
                case ERROR:
                    newCell.setCellErrorValue(sourceCell.getErrorCellValue());
                    values[colIndex] = String.valueOf(sourceCell.getErrorCellValue());
                    break;
                case FORMULA:
                    newCell.setCellFormula(sourceCell.getCellFormula());
                    values[colIndex] = getValue(errorRow, colIndex);
                    break;
                case NUMERIC:
                    newCell.setCellValue(sourceCell.getNumericCellValue());
                    values[colIndex] = String.valueOf(sourceCell.getNumericCellValue());
                    break;
                case STRING:
                    newCell.setCellValue(sourceCell.getRichStringCellValue());
                    values[colIndex] = sourceCell.getRichStringCellValue().getString();
                    break;
            }
        }
        return values;
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
            }
        } catch (IOException e) {
            logger.info("Error, file input stream corrupted");
        } catch (InvalidFormatException e) {
            logger.info("Error, invalid file format");
        }
        return firstColumn;
    }

    private MembershipInfo memberFromRow(Row row, Integer nameCol, Integer phoneCol, Integer emailCol, Integer provinceCol,
                                         Integer roleCol, Integer firstNameCol, Integer surNameCol, Integer affilCol) {
        if (phoneCol == null && emailCol == null) {
            logger.info("no phone or email col, exiting with error");
            throw new IllegalArgumentException("Error! One of email or phone number columns must be present");
        }
        if (nameCol == null && firstNameCol == null && surNameCol == null) {
            logger.info("no name columns, exiting with error");
            throw new IllegalArgumentException("Error! At least one of the name values must be set");
        }

        logger.debug("emailCol : {}, provinceCol: {}", emailCol, provinceCol);

        MembershipInfo info = new MembershipInfo();

        final String fullName = nameCol != null ? getValue(row, nameCol) : getValue(row, firstNameCol) + " " + getValue(row, surNameCol);
        info.setDisplayName(fullName);
        info.setFirstName(firstNameCol != null ? getValue(row, firstNameCol) : null);
        info.setSurname(surNameCol != null ? getValue(row, surNameCol) : null);

        final String phone = phoneCol != null ? getValue(row, phoneCol) : null;
        if (!StringUtils.isEmpty(phone) && !PhoneNumberUtil.testInputNumber(phone)) {
            logger.info("failed phone parsing, exiting, now");
            throw new InvalidPhoneNumberException("Bad phone number passed for phone");
        }
        info.setPhoneNumber(phoneCol != null ? phone : null);

        final String email = emailCol != null ? getValue(row, emailCol) : null;
        if (!StringUtils.isEmpty(email) && !EmailValidator.getInstance().isValid(email)) {
            throw new IllegalArgumentException("Bad email passed");
        }
        info.setMemberEmail(emailCol != null ? email : null);

        info.setRoleName(roleCol != null ? convertRoleName(getValue(row, roleCol)) : BaseRoles.ROLE_ORDINARY_MEMBER);

        if (!info.hasValidPhoneOrEmail()) {
            logger.info("don't have a valid phone or email, exiting");
            throw new IllegalArgumentException("Must have at least one of phone or email");
        }

        info.setProvince(provinceCol != null  ? convertProvince(getValue(row, provinceCol)) : null);
        info.setAffiliations(affilCol != null ? Arrays.asList(getValue(row, affilCol).split(",")) : null);

        logger.info("returning member info: {}", info);

        return info;
    }

    private boolean checkRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !CellType.BLANK.equals(cell.getCellTypeEnum()))
                return false;
        }
        return true;
    }

    private String getValue(Row row, Integer column) {
        return dataFormatter.formatCellValue(row.getCell(column), formulaEvaluator).trim();
    }

    private Province convertProvince(final String cellValue) {
        logger.debug("here's the province: {}", cellValue);
        if (Province.EN_PROVINCE_NAMES.containsKey(cellValue.toLowerCase().trim())) {
            logger.debug("okay, province: {}", cellValue);
            return Province.EN_PROVINCE_NAMES.get(cellValue.toLowerCase().trim());
        } else {
            try {
                return Province.valueOf("ZA_" + cellValue.toUpperCase().trim());
            } catch (Exception e) {
                return null;
            }
        }
    }

    private String convertRoleName(final String cellValue) {
        if (cellValue.toLowerCase().contains("organizer")) {
            return BaseRoles.ROLE_GROUP_ORGANIZER;
        }
        if (cellValue.toLowerCase().contains("committee")) {
            return BaseRoles.ROLE_COMMITTEE_MEMBER;
        }
        return BaseRoles.ROLE_ORDINARY_MEMBER;
    }

}
