package za.org.grassroot.webapp.controller.rest.broadcast;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.BroadcastSchedule;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.domain.notification.BroadcastNotification;
import za.org.grassroot.core.dto.BroadcastDTO;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.UrlShortener;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.socialmedia.FBPostBuilder;
import za.org.grassroot.integration.socialmedia.TwitterPostBuilder;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.broadcasts.BroadcastBroker;
import za.org.grassroot.services.broadcasts.BroadcastComponents;
import za.org.grassroot.services.broadcasts.BroadcastInfo;
import za.org.grassroot.services.broadcasts.EmailBroadcast;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.exception.FileCreationException;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController
@Api("/api/broadcast") @Slf4j
@RequestMapping(value = "/api/broadcast")
public class BroadcastController extends BaseRestController {

    private final BroadcastBroker broadcastBroker;
    private final AccountBillingBroker billingBroker;
    private final UrlShortener urlShortener;
    private final NotificationService notificationService;
    private final MemberDataExportBroker memberDataExportBroker;

    @Autowired
    public BroadcastController(JwtService jwtService, UserManagementService userManagementService, BroadcastBroker broadcastBroker, MediaFileBroker mediaFileBroker,
                               AccountBillingBroker billingBroker, UrlShortener urlShortener, NotificationService notificationService, MemberDataExportBroker memberDataExportBroker) {
        super(jwtService, userManagementService);
        this.broadcastBroker = broadcastBroker;
        this.billingBroker = billingBroker;
        this.urlShortener = urlShortener;
        this.notificationService = notificationService;
        this.memberDataExportBroker = memberDataExportBroker;
    }

    @RequestMapping(value = "/fetch/group/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the broadcasts attached to a group")
    public ResponseEntity<Page<BroadcastDTO>> fetchGroupSentBroadcasts(HttpServletRequest request, @PathVariable String groupUid,
                                                                       @RequestParam BroadcastSchedule broadcastSchedule,
                                                                       Pageable pageable) {
        Page<BroadcastDTO> broadcastDTOPage = new PageImpl<>(new ArrayList<>());
        final String userUid = getUserIdFromRequest(request);
        if(broadcastSchedule.equals(BroadcastSchedule.IMMEDIATE)){
            broadcastDTOPage = broadcastBroker.fetchSentGroupBroadcasts(groupUid, userUid, pageable);
        } else if(broadcastSchedule.equals(BroadcastSchedule.FUTURE)) {
            broadcastDTOPage = broadcastBroker.fetchScheduledGroupBroadcasts(groupUid, userUid, pageable);
        }

        if (broadcastDTOPage.getNumberOfElements() > 0) {
            log.info("broadcasts received, first one: {}", broadcastDTOPage.getContent().iterator().next());
        }
        return ResponseEntity.ok(broadcastDTOPage);
    }


    @RequestMapping(value = "/fetch/campaign/{campaignUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the broadcasts from a campaign")
    public ResponseEntity<List<BroadcastDTO>> fetchCampaignBroadcasts(HttpServletRequest request, @PathVariable String campaignUid) {
        return ResponseEntity.ok(broadcastBroker.fetchCampaignBroadcasts(campaignUid, getUserIdFromRequest(request)));
    }

    @RequestMapping(value = "/create/group/info/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch some basic information on the group, including social media options")
    public ResponseEntity<BroadcastInfo> getGroupBroadcastParams(HttpServletRequest request, @PathVariable String groupUid) {
        BroadcastInfo info = broadcastBroker.fetchGroupBroadcastParams(getUserIdFromRequest(request), groupUid);
        log.info("returning info: {}", info);
        return ResponseEntity.ok(info);
    }

    @RequestMapping(value = "/shorten/link", method = RequestMethod.GET)
    @ApiOperation(value = "Shorten a link for use in a broadcast short message")
    public ResponseEntity<String> shortenLink(@RequestParam String link, HttpServletRequest request) {
        User user = getUserFromRequest(request);
        if (user == null || user.getPrimaryAccount() == null) {
            throw new AccessDeniedException("Only users, and ones with accounts, can shorten a link");
        }
        return ResponseEntity.ok(urlShortener.shortenJoinUrls(link));
    }

    @RequestMapping(value = "/create/group/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a broadcast on the group", notes = "NB : this will be a heavy operation, so do it async")
    public ResponseEntity<BroadcastDTO> createGroupBroadcast(HttpServletRequest request,
                                                             @PathVariable String groupUid,
                                                             @RequestBody BroadcastCreateRequest createRequest) {
        log.info("creating broadcast! request looks like: {}", createRequest);

        String userUid = getUserIdFromRequest(request);

        BroadcastComponents bc = BroadcastComponents.builder()
                .broadcastId(createRequest.getBroadcastId())
                .title(createRequest.getTitle())
                .userUid(userUid)
                .groupUid(groupUid)
                .campaignBroadcast(false)
                .broadcastSchedule(createRequest.getSendType())
                .scheduledSendTime(createRequest.getSendDateTime())
                .provinces(createRequest.getProvinces())
                .topics(createRequest.getTopics())
                .taskTeams(createRequest.getTaskTeams())
                .affiliations(createRequest.getAffiliations())
                .joinMethods(createRequest.getJoinMethods())
                .joinDateCondition(createRequest.getJoinDateCondition())
                .joinDate(createRequest.getJoinDate())
                .filterLanguages(createRequest.getFilterLanguages())
                .filterNamePhoneOrEmail(createRequest.getFilterNamePhoneEmail())
                .skipSmsIfEmail(createRequest.isSkipSmsIfEmail())
                .build();

        fillInContent(createRequest, bc);

        String broadcastUid = broadcastBroker.sendGroupBroadcast(bc);

        return ResponseEntity.ok(broadcastBroker.fetchBroadcast(broadcastUid, userUid));
    }

    @RequestMapping(value = "/create/campaign/{campaignUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a broadcast for a campaign", notes = "NB : this will be a heavy operation, so do it async")
    public ResponseEntity<BroadcastDTO> createCampaignBroadcast(HttpServletRequest request, @PathVariable String campaignUid,
                                                                @RequestBody BroadcastCreateRequest createRequest) {

        log.info("broadcast send time millis = {}", createRequest.getSendDateTimeMillis());
        log.info("broadcast create request, = {}", createRequest);

        final String userUid = getUserIdFromRequest(request);
        BroadcastComponents bc = BroadcastComponents.builder()
                .title(createRequest.getTitle())
                .userUid(userUid)
                .campaignUid(campaignUid)
                .campaignBroadcast(true)
                .broadcastSchedule(createRequest.getSendType())
                .scheduledSendTime(createRequest.getSendDateTime())
                .provinces(createRequest.getProvinces())
                .topics(createRequest.getTopics())
                .build();

        fillInContent(createRequest, bc);
        String broadcastUid = broadcastBroker.sendGroupBroadcast(bc);

        return ResponseEntity.ok(broadcastBroker.fetchBroadcast(broadcastUid, userUid));
    }

    @RequestMapping(value = "/create/task/{taskType}/{taskUid}", method = RequestMethod.POST)
    public ResponseEntity<BroadcastDTO> sendTaskBroadcast(HttpServletRequest request, @PathVariable TaskType taskType,
                                                             @PathVariable String taskUid,
                                                             @RequestParam String message,
                                                             @RequestParam(required = false) Boolean sendToAll) {
        User user = getUserFromRequest(request);
        if (user.getPrimaryAccount() == null || !user.getPrimaryAccount().isBillPerMessage()) {
            throw new AccessDeniedException("Task broadcasts are only allowed for paid accounts");
        }

        log.info("sending a task broadcast, sendToAll = {}, message = {}", sendToAll, message);

        String broadcastUid = broadcastBroker.sendTaskBroadcast(user.getUid(), taskUid, taskType, sendToAll == null || !sendToAll, message);
        return ResponseEntity.ok(broadcastBroker.fetchBroadcast(broadcastUid, user.getUid()));
    }

    @RequestMapping(value = "/resend/{broadcastUid}", method = RequestMethod.POST)
    public ResponseEntity<BroadcastDTO> resentBroadcast(HttpServletRequest request, @PathVariable String broadcastUid,
                                                        boolean resendText, boolean resendEmail, boolean resendFb, boolean resendTwitter) {
        final String userUid = getUserIdFromRequest(request);
        final String resentUid = broadcastBroker.resendBroadcast(userUid, broadcastUid,
                resendText, resendEmail, resendFb, resendTwitter);
        return ResponseEntity.ok(broadcastBroker.fetchBroadcast(resentUid, userUid));
    }

    @RequestMapping(value = "/cost-this-month", method = RequestMethod.GET)
    public ResponseEntity<Long> getAccountCostThisMonth(HttpServletRequest request) {
        User user = getUserFromRequest(request);

        Account primaryAccount = user.getPrimaryAccount();
        TemporalAdjuster startOfMonth = (Temporal date) -> ((LocalDateTime)date).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        Instant firstDayOfMonth = LocalDateTime.now().with(startOfMonth).toInstant(OffsetDateTime.now().getOffset());
        long costSinceLastBill = billingBroker.calculateMessageCostsInPeriod(primaryAccount, firstDayOfMonth, Instant.now());
        return ResponseEntity.ok(costSinceLastBill);
    }

    @RequestMapping(value = "/error-report/{broadcastUid}/download", method = RequestMethod.GET)
    public ResponseEntity<byte[]> fetchTaskFailedNotifications(@PathVariable String broadcastUid,
            HttpServletRequest request) {

        try {
            User user = getUserFromRequest(request);

            String fileName = "task_error_report.xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            headers.add("Cache-Control", "no-cache");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            Broadcast broadcast = broadcastBroker.getBroadcast(broadcastUid);
            List <BroadcastNotification> notifications = notificationService.loadFailedNotificationsForBroadcast(user.getUid(), broadcast);

			XSSFWorkbook xls = memberDataExportBroker.exportNotificationErrorReport(notifications);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			xls.write(baos);
            return new ResponseEntity<>( baos.toByteArray(), headers, HttpStatus.OK);
        }catch (IOException e) {
			log.error("IO Exception generating spreadsheet!", e);
			throw new FileCreationException();
		}
		catch (AccessDeniedException e) {
			throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
		}
    }

    private void fillInContent(BroadcastCreateRequest createRequest, BroadcastComponents bc) {
        if (createRequest.isSendShortMessages()) {
            bc.setShortMessage(createRequest.getShortMessageString());
        }

        if (createRequest.isSendEmail()) {
            bc.setEmail(generateEmail(createRequest));
            log.info("attachment keys: {}", bc.getEmail().getAttachmentFileRecordUids());
        }

        if (createRequest.isPostToFacebook()) {
            bc.setFacebookPosts(generateFbPosts(bc.getUserUid(), createRequest));
        }

        if (createRequest.isPostToTwitter()) {
            bc.setTwitterPostBuilder(generateTweet(bc.getUserUid(), createRequest));
        }

    }

    private EmailBroadcast generateEmail(BroadcastCreateRequest request) {
        return EmailBroadcast.builder()
                .subject(request.getTitle())
                .content(request.getEmailContent())
                .deliveryRoute(DeliveryRoute.EMAIL_USERACCOUNT) // for the moment, default
                .attachmentFileRecordUids(request.getEmailAttachmentKeys())
                .build();
    }

    private List<FBPostBuilder> generateFbPosts(String userUid, BroadcastCreateRequest request) {
        log.info("incoming FB page Id = {}", request.getFacebookPages());
        return request.getFacebookPages().stream().map(fbp ->
            FBPostBuilder.builder()
                    .postingUserUid(userUid)
                    .facebookPageId(fbp)
                    .message(request.getFacebookContent())
                    .linkUrl(request.getFacebookLink())
                    .linkName(request.getFacebookLinkCaption())
                    .imageKey(request.getFacebookImageKey())
                    .imageMediaType(MediaFunction.BROADCAST_IMAGE)
                    .build()
        ).collect(Collectors.toList());
    }

    private TwitterPostBuilder generateTweet(String userUid, BroadcastCreateRequest request) {
        return TwitterPostBuilder.builder()
                .postingUserUid(userUid)
                .message(request.getTwitterContent())
                .imageKey(request.getTwitterImageKey())
                .imageMediaFunction(MediaFunction.BROADCAST_IMAGE)
                .build();
    }


}
