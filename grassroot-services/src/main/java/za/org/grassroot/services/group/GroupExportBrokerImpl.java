package za.org.grassroot.services.group;

import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GroupExportBrokerImpl implements GroupExportBroker {

    private final GroupBroker groupBroker;

    public GroupExportBrokerImpl(GroupBroker groupBroker) {
        this.groupBroker = groupBroker;
    }

    @Override
    public XSSFWorkbook exportGroup(String groupUid) {

        Group group = groupBroker.load(groupUid);

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Group members");

        generateHeader(workbook, sheet, new String[]{"Name", "Phone number", "Email"}, new int[]{2000, 5000, 5000});

        //table content stuff
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);

        XSSFCellStyle contentNumberStyle = workbook.createCellStyle();
        contentNumberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        //we are starting from 1 because row number 0 is header
        int rowIndex = 1;

        for (User user : group.getMembers()) {
            addRow(sheet, rowIndex, new String[]{user.getName(), user.getPhoneNumber(), user.getEmailAddress()});
            rowIndex++;
        }

        return workbook;
    }


    @Override
    public XSSFWorkbook exportMultipleGroupMembers(List<String> groupUids) {

        Set<User> uniqueUsers = new HashSet<>();
        for (String uid : groupUids) {
            Group group = groupBroker.load(uid);
            for (User user : group.getMembers()) {
                uniqueUsers.add(user);
            }
        }

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Members");

        generateHeader(workbook, sheet, new String[]{"Name", "Phone number", "Email", "Groups"}, new int[]{2000, 5000, 5000, 5000});

        //table content stuff
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);

        XSSFCellStyle contentNumberStyle = workbook.createCellStyle();
        contentNumberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        //we are starting from 1 because row number 0 is header

        int rowIndex = 1;
        for (User user : uniqueUsers) {
            StringBuilder sb = new StringBuilder();
            for (Membership membership : user.getMemberships()) {
                Group gr = membership.getGroup();
                if (groupUids.contains(gr.getUid())) {
                    sb.append(gr.getGroupName());
                    sb.append(",");
                }
            }
            String groupList = sb.toString();
            if (groupList.endsWith(","))
                groupList = groupList.substring(0, groupList.length() - 1);
            addRow(sheet, rowIndex, new String[]{user.getName(), user.getPhoneNumber(), user.getEmailAddress(), groupList});
        }


        return workbook;
    }

    private void generateHeader(XSSFWorkbook workbook, XSSFSheet sheet, String[] columnNames, int[] columnWidths) {

        XSSFCellStyle headerStyle = workbook.createCellStyle();

        XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        XSSFRow row = sheet.createRow(0);

        for (int i = 0; i < columnNames.length; i++) {
            String columnName = columnNames[i];
            XSSFCell cell = row.createCell(i);
            cell.setCellValue(columnName);
            sheet.setColumnWidth(i, columnWidths[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void addRow(XSSFSheet sheet, int rowIndex, String[] values) {

        XSSFRow row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            XSSFCell cell = row.createCell(i);
            cell.setCellValue(value);
        }
    }
}
