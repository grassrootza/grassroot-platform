package za.org.grassroot.services.livewire;

import java.util.Set;

/**
 * Created by luke on 2017/05/08.
 */
public interface LiveWireSendingBroker {
    // note: using a set to avoid possible duplicate mails, but watch ordering
    void sendLiveWireAlerts(Set<String> alertUids);
}
