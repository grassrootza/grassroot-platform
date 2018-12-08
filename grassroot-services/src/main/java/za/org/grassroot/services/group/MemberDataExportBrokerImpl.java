package za.org.grassroot.services.group;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.notification.NotificationSendError;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.core.dto.group.GroupLogDTO;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.campaign.CampaignStatsBroker;
import za.org.grassroot.services.task.TodoBroker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service @Slf4j
public class MemberDataExportBrokerImpl implements MemberDataExportBroker {

    @Value("${accounts.freeform.cost.standard:25}")
    private int accountMessageCost;

    @Value("${accounts.ussd.cost.standard:20}")
    private int accountUssdCost;

    private static final DateTimeFormatter STD_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    private final GroupBroker groupBroker;
    private final TodoBroker todoBroker;
    private final PermissionBroker permissionBroker;

    private final MessagingServiceBroker messageBroker;

    private CampaignStatsBroker campaignStatsBroker;
    private AccountBroker accountBroker;

    private final UserLogRepository userLogRepository;

    @Autowired
    public MemberDataExportBrokerImpl(UserRepository userRepository, MembershipRepository membershipRepository, GroupBroker groupBroker, TodoBroker todoBroker,
                                      PermissionBroker permissionBroker, MessagingServiceBroker messageBroker,UserLogRepository userLogRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.groupBroker = groupBroker;
        this.todoBroker = todoBroker;
        this.permissionBroker = permissionBroker;
        this.messageBroker = messageBroker;
        this.userLogRepository = userLogRepository;
    }

    @Autowired
    public void setCampaignStatsBroker(CampaignStatsBroker campaignStatsBroker) {
        this.campaignStatsBroker = campaignStatsBroker;
    }

    @Autowired
    public void setAccountBroker(AccountBroker accountBroker) {
        this.accountBroker = accountBroker;
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

    //Class for exporting the EXCEL file for subscribed whatsapp users.
    @Transactional()
    public XSSFWorkbook exportWhatsappOptedInUsers(){
        List<User> users = userRepository.findByWhatsAppOptedInTrue();
        return exportWhatsappOptedInUsers(users);
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

        generateHeader(workbook, sheet, new String[]{"Name", "Phone number", "Email", "Province", "Topics", "Affiliations"},
                new int[]{7000, 5000, 7000, 7000, 7000, 7000});

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

    private XSSFWorkbook exportWhatsappOptedInUsers(List<User> users){
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Whatsapp users");
        generateHeader(workbook, sheet, new String[]{"Phone number", "Date subscribed", "Channel"},
                new int[]{7000, 10000, 7000});
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);
        XSSFCellStyle contentNumberStyle = workbook.createCellStyle();
        contentNumberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        int rowIndex = 1;
        addRowFromUser(users,sheet, rowIndex);
        return workbook;
    }

    @Override
    @Transactional(readOnly = true)
    public XSSFWorkbook exportCampaignJoinedData(String campaignUid, String userUid) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        List<CampaignLog> campaignLogs = campaignStatsBroker.getCampaignJoinedAndBetter(Objects.requireNonNull(campaignUid));

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Campaign engagement");

        generateHeader(workbook, sheet, new String[]{"Name", "Phone number", "Email", "Province", "Most advanced action"},
                new int[]{7000, 5000, 7000, 7000, 7000});

        //table content stuff
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);

        XSSFCellStyle contentNumberStyle = workbook.createCellStyle();
        contentNumberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        //we are starting from 1 because row number 0 is header
        int rowIndex = 1;

        for (CampaignLog log : campaignLogs) {
            User rowUser = log.getUser();
            addRow(sheet, rowIndex, new String[]{
                    rowUser.getName(),
                    rowUser.getPhoneNumber(),
                    rowUser.getEmailAddress(),
                    Province.CANONICAL_NAMES_ZA.getOrDefault(rowUser.getProvince(), "Unknown"),
                    log.getCampaignLogType().name()});
            rowIndex++;
        }

        return workbook;
    }

    @Override
    public XSSFWorkbook exportCampaignBillingData(String campaignName, Map<String, String> billingCounts) {

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Campaign engagement");

        generateHeader(workbook, sheet, new String[]{"Billing Data For Campaign: " + billingCounts.get("campaign_name"), ""},
                new int[]{14000, 5000});

        final DateTimeFormatter df = DateTimeFormatter.ofPattern("d MMMM, yyyy");
        final String startDate = Instant.ofEpochMilli(Long.parseLong(billingCounts.get("start_time"))).atZone(DateTimeUtil.getSAST()).format(df);
        final String endDate = Instant.ofEpochMilli(Long.parseLong(billingCounts.get("end_time"))).atZone(DateTimeUtil.getSAST()).format(df);
        addRow(sheet, 1, new String[] {"From " + startDate + " to " + endDate, ""});
        addRow(sheet, 2, new String[] {"", ""});

        addRow(sheet, 3, new String[] { "Total USSD sessions", billingCounts.get("total_sessions")});
        addRow(sheet, 4, new String[] { "Campaign welcome SMSs", billingCounts.get("total_shares")});
        addRow(sheet, 5, new String[] { "Sharing SMSs", billingCounts.get("total_shares")});

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
        XSSFSheet sheet = workbook.createSheet("Error Messages");

        generateHeader(workbook, sheet, new String[]{"Time sent", "Phone number", "Email", "Errors"},
                new int[]{7000, 5000, 7000, 7000});

        //table content stuff
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);

        XSSFCellStyle contentNumberStyle = workbook.createCellStyle();
        contentNumberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        //we are starting from 1 because row number 0 is header
        int rowIndex = 1;

        for (Notification notification : notifications) {
            ArrayList<String> tableColumns = new ArrayList<>();
            tableColumns.add(notification.getCreatedDateTime() == null ? "" : STD_FORMATTER.format(notification.getCreatedDateTime()));
            tableColumns.add(notification.getTarget().getPhoneNumber() == null ? "" : notification.getTarget().getPhoneNumber());
            tableColumns.add(notification.getTarget().getEmailAddress() == null ? "" : notification.getTarget().getEmailAddress());
            for (NotificationSendError notificationSendError : notification.getSendingErrors()) {
                tableColumns.add(notificationSendError.getErrorTime() == null ? "" : STD_FORMATTER.format(notificationSendError.getErrorTime()) + " - " + notificationSendError.getErrorMessage());
            }
            String[] values = tableColumns.toArray(new String[0]);
            addRow(sheet, rowIndex, values);
            rowIndex++;

        }
        return workbook;
    }

    @Override
    public XSSFWorkbook exportNotificationStdReport(List<? extends Notification> notifications) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Error Messages");

        generateHeader(workbook, sheet, new String[]{"Time sent", "Phone number", "Email", "Status", "Receipt time", "Message"},
                new int[]{7000, 5000, 7000, 7000, 7000, 21000});

        // usual stuff that API makes annoyingly difficult to stick in a method
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);

        XSSFCellStyle contentNumberStyle = workbook.createCellStyle();
        contentNumberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        //we are starting from 1 because row number 0 is header
        int rowIndex = 1;

        for (Notification n: notifications) {
            String[] tableColumns = new String[6];
            tableColumns[0] = STD_FORMATTER.format(n.getCreatedDateTime());
            tableColumns[1] = n.getTarget().getPhoneNumber() == null ? "" : n.getTarget().getPhoneNumber();
            tableColumns[2] = n.getTarget().getEmailAddress() == null ? "" : n.getTarget().getEmailAddress();
            tableColumns[3] = n.getStatus().name();
            tableColumns[4] = STD_FORMATTER.format(n.getLastStatusChange());
            tableColumns[5] = n.getMessage();
            addRow(sheet, rowIndex, tableColumns);
            rowIndex++;
        }

        return workbook;
    }

    @Override
    public XSSFWorkbook exportAccountActivityReport(String accountUid, Instant start, Instant end) {
        Account account = accountBroker.loadAccount(accountUid);
        List<Group> accountGroups = account.getPaidGroups().stream().sorted(Comparator.comparing(Group::getName)).collect(Collectors.toList());

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Account activity");

        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        final String startDate = formatter.format(start.atZone(DateTimeUtil.getSAST()));
        final String endDate = formatter.format(end.atZone(DateTimeUtil.getSAST()));

        final Locale enZA = new Locale.Builder().setLanguage("en").setRegion("ZA").build();
        final NumberFormat numberFormat = NumberFormat.getCurrencyInstance(enZA);

        generateHeader(workbook, sheet, new String[]{
                        "Group name",
                        "Group size",
                        "Messages from " + startDate + " to " + endDate,
                        "Message cost (ZAR)"},
                new int[]{7000, 5000, 7000, 7000});

        // usual stuff that API makes annoyingly difficult to stick in a method
        XSSFCellStyle contentStyle = workbook.createCellStyle();
        XSSFFont contentFont = workbook.createFont();
        contentStyle.setFont(contentFont);

        XSSFCellStyle contentNumberStyle = workbook.createCellStyle();
        contentNumberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        //we are starting from 1 because row number 0 is header
        int rowIndex = 1;

        log.info("Counting notifications for {} groups", accountGroups.size());

        for (Group g: accountGroups) {
            long notificationCount = accountBroker.countChargedNotificationsForGroup(accountUid, g.getUid(), start, end);
            String[] tableColumns = new String[6];
            tableColumns[0] = g.getName();
            tableColumns[1] = "" + g.getMembers().size();
            tableColumns[2] = "" + notificationCount;
            tableColumns[3] = numberFormat.format((double) notificationCount * accountMessageCost);
            addRow(sheet, rowIndex, tableColumns);
            rowIndex++;
        }

        log.info("Finished assembling account sheet");

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
                    Province.CANONICAL_NAMES_ZA.getOrDefault(member.getUser().getProvince(), "Unknown"),
                    String.join(", ", member.getTopics()),
                    String.join(", ", member.getAffiliations())});
            rowIndex++;
        }
    }
//Methods for getting user details
//Adding users to rows and accessing the userLog to get date and interface type based on UserID
    private void addRowFromUser(List<User> users, XSSFSheet sheet, int rowIndex){
        for (User user:users) {
            UserLog userLog = getUserLog(user.getUid());

            addRow(sheet,rowIndex,new String[]{
                    user.getPhoneNumber(),
                    STD_FORMATTER.format(userLog.getCreationTime()),
                    userLog.getUserInterface().name()
            });
            rowIndex++;
        }
    }
//Looping through the Userlog to get the correct details based on UserUid
    private UserLog getUserLog(String userUid){
        List<UserLog> userLogs = userLogRepository.findByUserUidInAndUserLogType(Collections.singleton(userUid),
                UserLogType.USER_SUBSCRIBED_WHATSAPP);
        Collections.sort(userLogs,Comparator.comparing(UserLog::getCreationTime).reversed());
        return userLogs.get(0);
    }
}
