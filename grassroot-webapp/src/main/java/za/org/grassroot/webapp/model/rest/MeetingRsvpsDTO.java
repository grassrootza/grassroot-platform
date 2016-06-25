package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by luke on 2016/06/09.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeetingRsvpsDTO {

    private String meetingUid;

    private int numberInvited;
    private int numberYes;
    private int numberNo;
    private int numberNoReply;

    private boolean canViewRsvps;

    private HashMap<String, String> rsvpResponses;

    public MeetingRsvpsDTO(String meetingUid, ResponseTotalsDTO totals) {
        this.meetingUid = meetingUid;
        this.numberInvited = totals.getNumberOfUsers();
        this.numberYes = totals.getYes();
        this.numberNo = totals.getNo();
        this.numberNoReply = totals.getNumberNoRSVP();
        this.canViewRsvps = false;
        this.rsvpResponses = new HashMap<>();
    }

    public MeetingRsvpsDTO(String meetingUid, ResponseTotalsDTO totals, Map<User, EventRSVPResponse> details) {
        this(meetingUid, totals);
        this.canViewRsvps = true;
        for (User u : details.keySet()) {
            this.rsvpResponses.put(u.nameToDisplay(), details.get(u).toString());
        }
    }

    public String getMeetingUid() {
        return meetingUid;
    }

    public int getNumberInvited() {
        return numberInvited;
    }

    public int getNumberYes() {
        return numberYes;
    }

    public int getNumberNo() {
        return numberNo;
    }

    public int getNumberNoReply() {
        return numberNoReply;
    }

    public boolean isCanViewRsvps() {
        return canViewRsvps;
    }

    public HashMap<String, String> getRsvpResponses() {
        return rsvpResponses;
    }

    @Override
    public String toString() {
        return "MeetingRsvpsDTO{" +
                "meetingUid='" + meetingUid + '\'' +
                ", numberInvited=" + numberInvited +
                ", numberYes=" + numberYes +
                ", numberNo=" + numberNo +
                ", numberNoReply=" + numberNoReply +
                ", canViewRsvps=" + canViewRsvps +
                '}';
    }
}
