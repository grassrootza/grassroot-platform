package za.org.grassroot.core.enums;

/**
 * Created by aakilomar on 9/20/15.
 */
public enum EventRSVPResponse {
    YES ("Yes"),
    NO ("No"),
    NO_RESPONSE ("No response yet"),
    INVALID_RESPONSE ("Invalid RSVP"),
    MAYBE ("Maybe");



    public static EventRSVPResponse fromString(String rsvp) {

        if (rsvp != null) {
            for (EventRSVPResponse r : EventRSVPResponse.values()) {
                if (rsvp.equalsIgnoreCase(r.response)) {
                    return r;
                }
            }
        }
        return EventRSVPResponse.INVALID_RESPONSE;
    }


    private final String response;

    private EventRSVPResponse(String s) {
        response = s;
    }

    public boolean equalsName(String otherResponse) {
        return (otherResponse == null) ? false : response.equals(otherResponse);
    }

    public String toString() {
        return this.response;
    }
}
