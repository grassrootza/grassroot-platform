package za.org.grassroot.services.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.repository.LiveWireAlertRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luke on 2017/05/08.
 */
@Service
public class BatchedLiveWireSenderImpl implements BatchedLiveWireSender {

    private static final Logger logger = LoggerFactory.getLogger(BatchedLiveWireSenderImpl.class);

    private final LiveWireAlertRepository alertRepository;
    private final LiveWireSendingBroker sendingBroker;

    @Autowired
    public BatchedLiveWireSenderImpl(LiveWireAlertRepository alertRepository, LiveWireSendingBroker sendingBroker) {
        this.alertRepository = alertRepository;
        this.sendingBroker = sendingBroker;
    }

    @Override
    public void processPendingLiveWireAlerts() {
        Instant end = Instant.now();
        Instant start = end.minus(1L, ChronoUnit.HOURS);
        List<LiveWireAlert> alerts = alertRepository.findBySendTimeBetweenAndSentFalse(start, end);
        logger.info("LiveWire Alert Sender: processing {} alerts", alerts.size());
        if (!alerts.isEmpty()) {
            sendingBroker.sendLiveWireAlerts(alerts.stream()
                    .map(LiveWireAlert::getUid).collect(Collectors.toSet()));
        }
    }
}
