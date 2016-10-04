package za.org.grassroot.webapp.model;

import za.org.grassroot.core.domain.GroupChatSettings;

import java.util.List;

/**
 * Created by paballo on 2016/09/15.
 */
public class GroupChatSettingsDTO {

    private String groupUid;
    private String userUid;
    private boolean active;
    private boolean userInitiated;
    private boolean canReceive;
    private boolean canSend;
    private List<String> mutedUsersUids;

    public GroupChatSettingsDTO(){}

    public GroupChatSettingsDTO(GroupChatSettings groupChatSettings, List<String> mutedUsersUids){
        this.groupUid = groupChatSettings.getGroup().getUid();
        this.userUid = groupChatSettings.getUser().getUid();
        this.active = groupChatSettings.isActive();
        this.userInitiated = groupChatSettings.isUserInitiated();
        this.canReceive = groupChatSettings.isCanReceive();
        this.canSend = groupChatSettings.isCanSend();
        this.mutedUsersUids = mutedUsersUids;
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

    public List<String> getMutedUsersUids() {
        return mutedUsersUids;
    }
}

