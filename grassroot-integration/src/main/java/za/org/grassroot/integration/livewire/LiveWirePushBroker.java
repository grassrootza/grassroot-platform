package za.org.grassroot.integration.livewire;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.PropertySource;
import za.org.grassroot.integration.email.GrassrootEmail;

import java.util.List;

/**
 * Created by luke on 2017/05/08.
 */
public interface LiveWirePushBroker {
    boolean sendLiveWireEmails(List<GrassrootEmail> emails);
}