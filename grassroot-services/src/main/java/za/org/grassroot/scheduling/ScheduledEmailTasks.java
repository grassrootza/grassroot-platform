package za.org.grassroot.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.AnalyticalService;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Created by luke on 2016/10/25.
 */
@Component
@ConditionalOnProperty(name = "grassroot.email.enabled", havingValue = "true",  matchIfMissing = false)
public class ScheduledEmailTasks {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledEmailTasks.class);

    @Value("${grassroot.daily.admin.email:false}")
    private boolean sendDailyAdminMail;

    @Value("${grassroot.system.mail:contact@grassroot.org.za}")
    private String systemEmailAddress;

    private MessagingServiceBroker messagingServiceBroker;
    private AnalyticalService analyticalService;

    @Autowired
    public ScheduledEmailTasks(MessagingServiceBroker messagingServiceBroker, AnalyticalService analyticalService) {
        this.messagingServiceBroker = messagingServiceBroker;
        this.analyticalService = analyticalService;
    }

    @Scheduled(cron = "${grassroot.admin.mail.cron.trigger: 0 0 5 * * ?}")
    public void sendSystemStatsEmail() {
        if (sendDailyAdminMail) {
            logger.info("Sending system stats email ... ");

            LocalDateTime yesterday = LocalDateTime.now().minusDays(1L);
            LocalDateTime now = LocalDateTime.now();

            long totalUsers = analyticalService.countAllUsers();
            long usersYesterday = analyticalService.countUsersCreatedInInterval(yesterday, now);
            long totalInitiated = analyticalService.countUsersThatHaveInitiatedSession();
            long initiatedYesterday = analyticalService.countUsersCreatedAndInitiatedInPeriod(yesterday, now);
            long androidTotal = analyticalService.countUsersThatHaveAndroidProfile();
            long webTotal = analyticalService.countUsersThatHaveWebProfile();

            final String userLine = String.format("Grassroot has reached %d users, of whom %d were added yesterday. A " +
                    "total of %d users have initiated a session, of which %d were yesterday. There have been %d Android " +
                    "users, and %d web users.%n", totalUsers, usersYesterday, totalInitiated, initiatedYesterday, androidTotal, webTotal);

            long allMeetings = analyticalService.countAllEvents(EventType.MEETING);
            long allVotes = analyticalService.countAllEvents(EventType.VOTE);
            long allTodos = analyticalService.countAllTodos();

            long mtgsYesterday = analyticalService.countEventsCreatedInInterval(yesterday, now, EventType.MEETING);
            long votesYesterday = analyticalService.countEventsCreatedInInterval(yesterday, now, EventType.VOTE);
            long todosYesterday = analyticalService.countTodosRecordedInInterval(yesterday, now);

            final String taskLine = String.format("Yesterday %d meetings were called, %d votes and %d todos. In total, there" +
                    "have been %d meetings, %d votes and %d todos called through Grassroot.%n%n", mtgsYesterday, votesYesterday, todosYesterday,
                    allMeetings, allVotes, allTodos);

            long groupsTotal = analyticalService.countActiveGroups();
            int groupsYesterday = analyticalService.countGroupsCreatedInInterval(yesterday, now);
            int groupsGeo = analyticalService.countGroupsWithGeoLocationData();

            final String groupLine = String.format("Yesterday %d groups were created. In total there are %d groups on the platform, " +
                    "of which %d have location data.%n%n", groupsYesterday, groupsTotal, groupsGeo);

            long safetyTotal = analyticalService.countSafetyEventsInInterval(null, null);
            long safetyYesterday = analyticalService.countSafetyEventsInInterval(yesterday, now);

            final String safetyLine = String.format("Finally, yesterday %d safety events were created. In total, %d safety" +
                    " alerts have been triggered.%n%n", safetyYesterday, safetyTotal);

            final String emailBody = "Good morning,\n" + userLine + taskLine + groupLine + safetyLine + "\nGrassroot";

            messagingServiceBroker.sendEmail(Arrays.asList(systemEmailAddress.split(",")),
                    new GrassrootEmail.EmailBuilder("System Email").content(emailBody).build());
        }
    }

}
