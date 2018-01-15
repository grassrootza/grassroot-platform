package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.io.Serializable;
import java.util.List;

/**
 * Created by aakilomar on 9/21/15.
 */
public class ResponseTotalsDTO implements Serializable {

    // private final static Logger log = LoggerFactory.getLogger(ResponseTotalsDTO.class);

    private int yes;
    private int no;
    private int maybe;
    private int invalid;
    private int numberOfUsers;

    private ResponseTotalsDTO() {
        // for make test (but keep private so it's explicit that this should not be called)
    }

    public static ResponseTotalsDTO makeForTest(int yes, int no, int maybe, int invalid, int numberOfUsers) {
        ResponseTotalsDTO totalsDTO = new ResponseTotalsDTO();
        totalsDTO.yes = yes;
        totalsDTO.no = no;
        totalsDTO.maybe = maybe;
        totalsDTO.invalid = invalid;
        totalsDTO.numberOfUsers = numberOfUsers;
        return totalsDTO;
    }

    public ResponseTotalsDTO(List<EventLog> eventLogs, Event event) {
        this();
        this.yes = (int) eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.YES)).count();
        this.no = (int) eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.NO)).count();
        this.maybe = (int) eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.MAYBE)).count();
        this.invalid = (int) eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.INVALID_RESPONSE)).count();
        this.numberOfUsers = event.isAllGroupMembersAssigned() || event.getAllMembers() == null ?
                event.getAncestorGroup().getMemberships().size() :
                event.getAllMembers().size();
    }

    public int getNumberNoRSVP() {
        return numberOfUsers - (yes + no + maybe + invalid);
    }

    public int getYes() {
        return yes;
    }

    public void setYes(int yes) {
        this.yes = yes;
    }

    public int getNo() {
        return no;
    }

    public void setNo(int no) {
        this.no = no;
    }

    public int getMaybe() {
        return maybe;
    }

    public void setMaybe(int maybe) {
        this.maybe = maybe;
    }

    public int getInvalid() {
        return invalid;
    }

    public void setInvalid(int invalid) {
        this.invalid = invalid;
    }

    public int getNumberOfUsers() {
        return numberOfUsers;
    }

    public void setNumberOfUsers(int numberOfUsers) {
        this.numberOfUsers = numberOfUsers;
    }

    @Override
    public String toString() {
        return "ResponseTotalsDTO{" +
                "yes=" + yes +
                ", no=" + no +
                ", maybe=" + maybe +
                ", invalid=" + invalid +
                ", numberOfUsers=" + numberOfUsers +
                ", numberNoRSVP=" + getNumberNoRSVP() +
                '}';
    }
}
