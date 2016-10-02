package za.org.grassroot.webapp.model;

import za.org.grassroot.core.domain.GroupChatSettings;

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

    public MessengerSettingsDTO(GroupChatSettings groupChatSettings){
        this.groupUid = groupChatSettings.getGroup().getUid();
        this.userUid = groupChatSettings.getUser().getUid();
        this.active = groupChatSettings.isActive();
        this.userInitiated = groupChatSettings.isUserInitiated();
        this.canReceive = groupChatSettings.isCanReceive();
        this.canSend = groupChatSettings.isCanSend();
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

