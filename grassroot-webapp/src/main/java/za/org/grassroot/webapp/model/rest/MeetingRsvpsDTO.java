package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by luke on 2016/06/09.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeetingRsvpsDTO {

    private static final Logger logger = LoggerFactory.getLogger(MeetingRsvpsDTO.class);

    private String meetingUid;

    private int numberInvited;
    private int numberYes;
    private int numberNo;
    private int numberNoReply;

    private boolean canViewRsvps;

    private LinkedHashMap<String, String> rsvpResponses;

    public MeetingRsvpsDTO(String meetingUid, ResponseTotalsDTO totals) {
        this.meetingUid = meetingUid;
        this.numberInvited = totals.getNumberOfUsers();
        this.numberYes = totals.getYes();
        this.numberNo = totals.getNo();
        this.numberNoReply = totals.getNumberNoRSVP();
        this.canViewRsvps = false;
        this.rsvpResponses = new LinkedHashMap<>();
    }

    public MeetingRsvpsDTO(String meetingUid, ResponseTotalsDTO totals, Map<User, EventRSVPResponse> details) {
        this(meetingUid, totals);
        this.canViewRsvps = true;
        this.rsvpResponses = sortRsvpResponses(details);
    }

    private LinkedHashMap<String, String> sortRsvpResponses(Map<User, EventRSVPResponse> details) {

        LinkedHashMap<String, String> result = new LinkedHashMap<>();

	    logger.info("event rsvp details = {}", details);

	    final Comparator<Map.Entry<User, EventRSVPResponse>> byRsvpResponse =
                Comparator.comparing(Map.Entry::getValue);
        final Comparator<Map.Entry<User, EventRSVPResponse>> byUserName =
                Comparator.comparing(Map.Entry::getKey);

        // note : using map and collector might be better here, though which collector, and function reference, mean leave till later
	    details.entrySet()
                .stream()
                .sorted(byRsvpResponse.thenComparing(byUserName))
                .forEach(entry -> result.put(entry.getKey().nameToDisplay(), entry.getValue().toString()));

	    logger.debug("Sorted entry set : " + result.toString());

        return result;
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
