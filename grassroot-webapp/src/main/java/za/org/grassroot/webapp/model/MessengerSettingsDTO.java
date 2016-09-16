package za.org.grassroot.webapp.model;

import za.org.grassroot.core.domain.MessengerSettings;

/**
 * Created by paballo on 2016/09/15.
 */
public class MessengerSettingsDTO {

    private String groupUid;
    private String userUid;
    private boolean active;
    private boolean userInitiated;
    private boolean canReceive;
    private boolean canSend;

    public MessengerSettingsDTO(){}

    public MessengerSettingsDTO(MessengerSettings messengerSettings){
        this.groupUid = messengerSettings.getGroup().getUid();
        this.userUid = messengerSettings.getUser().getUid();
        this.active = messengerSettings.isActive();
        this.userInitiated = messengerSettings.isUserInitiated();
        this.canReceive = messengerSettings.isCanReceive();
        this.canSend = messengerSettings.isCanSend();
    }

    public String getGroupUid() {
        return groupUid;
    }

    public String getUserUid() {
        return userUid;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isUserInitiated() {
        return userInitiated;
    }

    public boolean isCanReceive() {
        return canReceive;
    }

    public boolean isCanSend() {
        return canSend;
    }


}

