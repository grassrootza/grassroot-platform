package za.org.grassroot.services.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.repository.LiveWireAlertRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luke on 2017/05/08.
 * todo : keep watch on fragility, now that this is moved out of quartz
 */
@Component
public class ScheduledLiveWireSender {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledLiveWireSender.class);

    private final LiveWireAlertRepository alertRepository;
    private final LiveWireSendingBroker sendingBroker;

    @Autowired
    public ScheduledLiveWireSender(LiveWireAlertRepository alertRepository, LiveWireSendingBroker sendingBroker) {
        this.alertRepository = alertRepository;
        this.sendingBroker = sendingBroker;
    }

    @Scheduled(cron = "0 0/1 * * * ?")
    public void processPendingLiveWireAlerts() {
        logger.debug("Processing pending LiveWire alerts");
        Instant end = Instant.now();
        Instant start = end.minus(1L, ChronoUnit.HOURS);
        List<LiveWireAlert> alerts = alertRepository.findBySendTimeBetweenAndSentFalse(start, end);
        if (!alerts.isEmpty()) {
            logger.info("LiveWire Alert Sender: processing {} alerts", alerts.size());
            sendingBroker.sendLiveWireAlerts(alerts.stream()
                    .map(LiveWireAlert::getUid).collect(Collectors.toSet()));
        }
    }
}
