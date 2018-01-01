package za.org.grassroot.integration.livewire;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.repository.LiveWireAlertRepository;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2017/05/08.
 */
@Service
public class LiveWirePushBrokerImpl implements LiveWirePushBroker {

    @Value("${grassroot.livewire.from.address:livewire@grassroot.org.za}")
    private String livewireEmailAddress;

    private final MessagingServiceBroker messageServiceBroker;
    private final LiveWireAlertRepository liveWireAlertRepository;

    @Autowired
    public LiveWirePushBrokerImpl(MessagingServiceBroker messageServiceBroker, Environment environment, LiveWireAlertRepository liveWireAlertRepository) {
        this.messageServiceBroker = messageServiceBroker;
        this.liveWireAlertRepository = liveWireAlertRepository;
    }

    @Async
    @Override
    @Transactional
    public void sendLiveWireEmails(String alertUid, List<GrassrootEmail> emails) {
        LiveWireAlert alert = liveWireAlertRepository.findOneByUid(alertUid);

        List<String> toAddresses = new ArrayList<>();
        emails.forEach(e -> {
            e.setFromAddress(livewireEmailAddress);
            toAddresses.add(e.getAddress());
        });

        // todo : check if response is okay
        messageServiceBroker.sendEmail(toAddresses, emails.get(0));
        alert.setSent(true);
    }
}