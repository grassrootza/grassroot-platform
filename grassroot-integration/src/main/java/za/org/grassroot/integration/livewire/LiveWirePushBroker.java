package za.org.grassroot.integration.livewire;

import za.org.grassroot.integration.email.GrassrootEmail;

import java.util.List;

/**
 * Created by luke on 2017/05/08.
 */
public interface LiveWirePushBroker {
    void sendLiveWireEmails(String alertUid, List<GrassrootEmail> emails);
}