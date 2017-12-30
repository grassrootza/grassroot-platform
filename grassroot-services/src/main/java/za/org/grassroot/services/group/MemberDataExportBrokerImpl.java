package za.org.grassroot.services.group;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.task.TodoBroker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service @Slf4j
public class MemberDataExportBrokerImpl implements MemberDataExportBroker {

    private final UserRepository userRepository;

    private final GroupBroker groupBroker;
    private final TodoBroker todoBroker;
    private final PermissionBroker permissionBroker;

    private final MessagingServiceBroker messageBroker;

    public MemberDataExportBrokerImpl(UserRepository userRepository, GroupBroker groupBroker, TodoBroker todoBroker,
                                      PermissionBroker permissionBroker, MessagingServiceBroker messageBroker) {
        this.userRepository = userRepository;
        this.groupBroker = groupBroker;
        this.todoBroker = todoBroker;
        this.permissionBroker = permissionBroker;
        this.messageBroker = messageBroker;
    }

    @Override
    public XSSFWorkbook exportGroup(String groupUid, String userUid) {

        // todo : include tags where consistent
        Group group = groupBroker.load(groupUid);
        User exporter = userRepository.findOneByUid(userUid);

        permissionBroker.validateGroupPermission(exporter, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

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
            addRow(sheet, rowIndex, new String[]{user.getDisplayName(), user.getPhoneNumber(), user.getEmailAddress()});
            rowIndex++;
        }

        return workbook;
    }


    @Override
    public XSSFWorkbook exportMultipleGroupMembers(List<String> userGroupUids, List<String> groupsToExportUids) {

        Set<User> uniqueUsers = new HashSet<>();
        for (String uid : groupsToExportUids) {
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
            List<Membership> memberships = user.getMemberships().stream().sorted(Comparator.comparing(o -> o.getGroup().getGroupName())).collect(Collectors.toList());
            for (Membership membership : memberships) {
                Group gr = membership.getGroup();
                if (gr.isActive() && userGroupUids.contains(gr.getUid())) {
                    sb.append(gr.getGroupName());
                    sb.append(", ");
                }
            }
            String groupList = sb.toString();
            if (groupList.endsWith(", "))
                groupList = groupList.substring(0, groupList.length() - 2);
            addRow(sheet, rowIndex, new String[]{user.getDisplayName(), user.getPhoneNumber(), user.getEmailAddress(), groupList});
            rowIndex++;
        }


        return workbook;
    }

    @Override
    public XSSFWorkbook exportTodoData(String userUid, String todoUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        Todo todo = todoBroker.load(todoUid);
        List<TodoAssignment> todoAssignments = todoBroker.fetchAssignedUserResponses(userUid, todoUid, false, true, false);

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("TodoResponses");

        generateHeader(workbook, sheet, new String[]{
                "Member name", "Phone number", "Responded?", "Response ('" + todo.getResponseTag() + "')", "Date of response"},
                new int[]{7000, 5000, 3000, 7000, 10000});

        //table content stuff
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);

        XSSFCellStyle contentNumberStyle = workbook.createCellStyle();
        contentNumberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        //we are starting from 1 because row number 0 is header
        int rowIndex = 1;
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.ENGLISH).withZone(ZoneId.systemDefault());
        for (TodoAssignment assignment : todoAssignments) {

            addRow(sheet, rowIndex, new String[]{
                    assignment.getUser().getName(),
                    assignment.getUser().getPhoneNumber(),
                    String.valueOf(assignment.isHasResponded()),
                    assignment.getResponseText(),
                    assignment.getResponseTime() == null ? "" : formatter.format(assignment.getResponseTime()) }); // todo: format
            rowIndex++;
        }
        //assignment.getResponseTime() == null ? "" : assignment.getResponseTime().toString()
        return workbook;
    }

    @Async
    @Override
    public void emailTodoResponses(String userUid, String todoUid, String emailAddress) {
        log.info("generating email of todo responses ... should be on background thread");

        Objects.requireNonNull(emailAddress); // export will check user and todo

        Todo todo = todoBroker.load(todoUid);
        File workbookFile = writeToFile(exportTodoData(userUid, todoUid), "todo_responses");

        if (workbookFile == null) {
            log.error("Could not generate workbook file to send");
        } else {
            // todo : make body more friendly, and create a special email address (no-reply) for from
            GrassrootEmail email = new GrassrootEmail.EmailBuilder("Grassroot: todo responses")
                    .from("no-reply@grassroot.org.za")
                    .subject(todo.getName() + "todo response")
                    .attachment("todo_responses", workbookFile)
                    .content("Good day,\nKindly find the attached responses for the above mentioned todo.")
                    .build();

            log.info("email assembled, putting it in the queue");
            messageBroker.sendEmail(Collections.singletonList(emailAddress), email);
        }
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

    private File writeToFile(XSSFWorkbook workbook, String fileName) {
        try {
            File outputFile = File.createTempFile(fileName, "xls");
            FileOutputStream fos = new FileOutputStream(outputFile);
            workbook.write(fos);
            fos.flush();
            fos.close();
            return outputFile;
        } catch (IOException e) {
            log.error("Error generating temp file from workbook", e);
            return null;
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
