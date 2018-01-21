package za.org.grassroot.webapp.model.rest;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.enums.ActionLogType;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;

import java.time.Instant;
import java.util.List;

@Getter @Slf4j
public class MemberActivityDTO {

    String groupUid;
    String memberUid;

    ActionLogType actionLogType;
    public String logSubType;

    public String nameOfRelatedEntity;
    public String auxField;

    public Instant dateOfLog;
    public long dateOfLogEpochMillis;

    public List<String> topics;

    public MemberActivityDTO(String memberUid, String groupUid, ActionLog actionLog) {
        this.groupUid = groupUid;
        this.memberUid = memberUid;

        this.dateOfLog = actionLog.getCreationTime();
        this.dateOfLogEpochMillis = actionLog.getCreationTime().toEpochMilli();

        if (actionLog instanceof EventLog) {
            setForEvent((EventLog) actionLog);
        } else if (actionLog instanceof TodoLog) {
            setForTodo((TodoLog) actionLog);
        } else if (actionLog instanceof CampaignLog) {
            setForCampaign((CampaignLog) actionLog);
        } else if (actionLog instanceof GroupLog) {
            setForGroup((GroupLog) actionLog);
        } else if (actionLog instanceof LiveWireLog) {
            setForLiveWire((LiveWireLog) actionLog);
        } else {
            // not throwing error as this will almost only be called in a stream (and under tight constraints)
            log.error("Error! Member activity DTO called with illegal log type");
        }
    }

    private void setForEvent(EventLog eventLog) {
        this.actionLogType = ActionLogType.EVENT_LOG;
        this.logSubType = eventLog.getEvent().getEventType() + "_" + eventLog.getEventLogType().name();
        this.nameOfRelatedEntity = eventLog.getEvent().getName();
        if (eventLog.getEvent().getEventType().equals(EventType.MEETING) && eventLog.getEventLogType().equals(EventLogType.RSVP)) {
            this.auxField = eventLog.getResponse().toString();
        }

        this.topics = eventLog.getEvent().getTopics();
    }

    private void setForCampaign(CampaignLog campaignLog) {
        this.actionLogType = ActionLogType.CAMPAIGN_LOG;
        this.logSubType = campaignLog.getCampaignLogType().name();

        this.nameOfRelatedEntity = campaignLog.getCampaign().getName();
        this.topics = campaignLog.getCampaign().getTopics();
    }

    private void setForGroup(GroupLog groupLog) {
        this.actionLogType = ActionLogType.GROUP_LOG;
        this.logSubType = groupLog.getGroupLogType().name();
        this.nameOfRelatedEntity = groupLog.getTarget().getName();
        // todo : add in aux field, based on type of log
    }

    private void setForTodo(TodoLog todoLog) {
        this.actionLogType = ActionLogType.TODO_LOG;
        this.logSubType = todoLog.getType().name();
        this.nameOfRelatedEntity = todoLog.getTodo().getName();
        // todo : add in an aux field if appropriate
    }

    private void setForLiveWire(LiveWireLog liveWireLog) {
        this.actionLogType = ActionLogType.LIVEWIRE_LOG;
        this.logSubType = liveWireLog.getType().name();
        this.nameOfRelatedEntity = liveWireLog.getAlert().getHeadline();
    }

}
