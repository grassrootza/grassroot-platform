package za.org.grassroot.services.group;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.util.StringUtils;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.notification.NotificationSendError;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.core.dto.group.GroupLogDTO;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserRepository;
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
    private final MembershipRepository membershipRepository;

    private final GroupBroker groupBroker;
    private final TodoBroker todoBroker;
    private final PermissionBroker permissionBroker;

    private final MessagingServiceBroker messageBroker;

    public MemberDataExportBrokerImpl(UserRepository userRepository, MembershipRepository membershipRepository, GroupBroker groupBroker, TodoBroker todoBroker,
                                      PermissionBroker permissionBroker, MessagingServiceBroker messageBroker) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.groupBroker = groupBroker;
        this.todoBroker = todoBroker;
        this.permissionBroker = permissionBroker;
        this.messageBroker = messageBroker;
    }

    @Override
    @Transactional(readOnly = true)
    public XSSFWorkbook exportGroup(String groupUid, String userUid) {

        Group group = groupBroker.load(groupUid);
        User exporter = userRepository.findOneByUid(userUid);

        permissionBroker.validateGroupPermission(exporter, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        List<Membership> memberships = group.getMemberships().stream()
                .sorted(Comparator.comparing(Membership::getDisplayName)).collect(Collectors.toList());
        return exportGroupMembers(memberships);
    }

    @Override
    @Transactional(readOnly = true)
    public XSSFWorkbook exportGroupErrorReport(String groupUid, String userUid) {
        Group group = groupBroker.load(groupUid);
        User exporter = userRepository.findOneByUid(userUid);

        permissionBroker.validateGroupPermission(exporter, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        List<Membership> membersWithErrorFlag = group.getMemberships().stream()
                .filter(m -> m.getUser().isContactError())
                .sorted(Comparator.comparing(Membership::getDisplayName))
                .collect(Collectors.toList());

        return exportGroupMembers(membersWithErrorFlag);
    }

    @Override
    public XSSFWorkbook exportGroupMembersFiltered(String groupUid, String userUid, List<String> memberUids) {
        Group group = groupBroker.load(groupUid);
        User exporter = userRepository.findOneByUid(userUid);

        permissionBroker.validateGroupPermission(exporter, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        List<Membership> memberships = membershipRepository.findByGroupAndUserUidIn(group, memberUids);
        return exportGroupMembers(memberships);
    }

    private XSSFWorkbook exportGroupMembers(List<Membership> memberships) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Group members");

        generateHeader(workbook, sheet, new String[]{"Name", "Phone number", "Email", "Topics", "Affiliations"},
                new int[]{7000, 5000, 7000, 7000, 7000});

        //table content stuff
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);

        XSSFCellStyle contentNumberStyle = workbook.createCellStyle();
        contentNumberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        //we are starting from 1 because row number 0 is header
        int rowIndex = 1;

        addRowFromMember(memberships, sheet, rowIndex);

        return workbook;
    }

    @Override
    public XSSFWorkbook exportMultipleGroupMembers(List<String> userGroupUids, List<String> groupsToExportUids) {

        Set<User> uniqueUsers = new HashSet<>();
        for (String uid : groupsToExportUids) {
            Group group = groupBroker.load(uid);
            uniqueUsers.addAll(group.getMembers());
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
        log.info("pre-sort assignments: {}", todoAssignments);
        todoAssignments.sort((t1, t2) -> {
            if (t1.isHasResponded() != t2.isHasResponded()) {
                return t1.isHasResponded() ? -1 : 1;
            } else if (t1.emptyResponse() || t2.emptyResponse()) { // shouldn't happen, but in case
                return !t1.emptyResponse() && t2.emptyResponse() ? 1 : !t2.emptyResponse() ? 0 : -1;
            } else {
                return t1.getResponseText().compareTo(t2.getResponseText());
            }
        });
        log.info("post-sort assignments: {}", todoAssignments);

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("TodoResponses");

        final String responseHeader = StringUtils.isEmpty(todo.getResponseTag()) ? "Response"
                : "Response ('" + todo.getResponseTag() + "')";
        generateHeader(workbook, sheet, new String[]{
                "Member name", "Phone number", "Email", "Responded?", responseHeader, "Date of response"},
                new int[]{7000, 5000, 7000, 5000, 7000, 10000});

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
                    assignment.getUser().getEmailAddress(),
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
        File workbookFile = writeToFile(exportTodoData(userUid, todoUid), "action_item_responses");

        if (workbookFile == null) {
            log.error("Could not generate workbook file to send");
        } else {
            GrassrootEmail email = new GrassrootEmail.EmailBuilder("Grassroot: action item responses")
                    .toAddress(emailAddress)
                    .fromName("Grassroot System")
                    .fromAddress("notifications@grassroot.org.za")
                    .subject(todo.getName() + ": response sheet")
                    .attachment("action_item_responses", workbookFile)
                    .content("Good day,\n\n" +
                            "Kindly find the attached responses for your action item, " + todo.getName() + ", which was just " +
                            "requested. For any details or questions, log in at https://www.grassroot.org.za or dial *134*1994#.\n\n" +
                            "Regards,\n\n" +
                            "Grassroot\n\n" +
                            "Please do not reply to this mail")
                    .build();

            log.info("email assembled, putting it in the queue");
            messageBroker.sendEmail(email);
        }
    }

    @Override
    public XSSFWorkbook exportInboundMessages(List<GroupLogDTO> inboundMessages) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Group inbound messages");

        generateHeader(workbook, sheet, new String[]{"User name", "Phone number", "Message", "Time created"}, new int[]{5000, 5000, 15000, 5000});

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

        for (GroupLogDTO inboundMessage : inboundMessages) {
            addRow(sheet, rowIndex, new String[]{inboundMessage.getTargetUser().getDisplayName(),
                    inboundMessage.getTargetUser().getPhoneNumber(), inboundMessage.getDescription(), formatter.format(inboundMessage.getCreatedDateTime())});
            rowIndex++;

        }

        return workbook;
    }

    @Override
    public XSSFWorkbook exportNotificationErrorReport(List<? extends Notification> notifications) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Group members");

        generateHeader(workbook, sheet, new String[]{"Created time", "Phone number", "Email"},
                new int[]{7000, 5000, 7000, 7000});

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

        for (Notification notification : notifications) {
            ArrayList<String> tableColumns = new ArrayList<>();
            tableColumns.add(notification.getCreatedDateTime() == null ? "" : formatter.format(notification.getCreatedDateTime()));
            tableColumns.add(notification.getTarget().getPhoneNumber() == null ? "" : notification.getTarget().getPhoneNumber());
            tableColumns.add(notification.getTarget().getEmailAddress() == null ? "" : notification.getTarget().getEmailAddress());
            for (NotificationSendError notificationSendError : notification.getSendingErrors()) {
                tableColumns.add(notificationSendError.getErrorTime() == null ? "" : formatter.format(notificationSendError.getErrorTime()) + " - " + notificationSendError.getErrorMessage());
            }
            String[] values = tableColumns.toArray(new String[0]);
            addRow(sheet, rowIndex, values);
            rowIndex++;

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

    private void addRowFromMember(List<Membership> memberships, XSSFSheet sheet, int rowIndex) {
        for (Membership member : memberships) {
            addRow(sheet, rowIndex, new String[]{
                    member.getDisplayName(),
                    member.getUser().getPhoneNumber(),
                    member.getUser().getEmailAddress(),
                    String.join(", ", member.getTopics()),
                    String.join(", ", member.getAffiliations())});
            rowIndex++;
        }
    }
}
