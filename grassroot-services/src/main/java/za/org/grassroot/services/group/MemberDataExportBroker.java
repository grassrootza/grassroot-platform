package za.org.grassroot.services.group;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.dto.group.GroupLogDTO;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MemberDataExportBroker {

    Optional<File> exportGroup(String groupUid, String userUid);

    XSSFWorkbook exportGroupErrorReport(String groupUid, String userUid);

    XSSFWorkbook exportGroupMembersFiltered(String groupUid, String userUid, List<String> memberUids);

    XSSFWorkbook exportCampaignJoinedData(String campaignUid, String userUid);

    XSSFWorkbook exportCampaignBillingData(String campaignUid, Map<String, String> billingCounts);

    XSSFWorkbook exportTodoData(String userUid, String todoUid);

    void emailTodoResponses(String userUid, String todoUid, String emailAddress);

    XSSFWorkbook exportInboundMessages(List<GroupLogDTO> inboundMessages);

    XSSFWorkbook exportNotificationErrorReport(List<? extends Notification> notifications);

    XSSFWorkbook exportNotificationStdReport(List<? extends Notification> notifications);

    XSSFWorkbook exportAccountActivityReport(String accountUid, Instant start, Instant end);

    XSSFWorkbook exportWhatsappOptedInUsers();

    XSSFWorkbook exportAccountBillingData(List<Campaign> campaigns,Instant start, Instant end);

}
