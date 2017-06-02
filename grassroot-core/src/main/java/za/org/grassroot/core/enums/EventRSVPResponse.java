package za.org.grassroot.core.enums;

/**
 * Created by aakilomar on 9/20/15.
 */
public enum EventRSVPResponse {

    YES,
    NO,
    NO_RESPONSE,
    INVALID_RESPONSE,
    MAYBE,
    VOTE_OPTION;

    public static EventRSVPResponse fromString(String rsvp) {
        if (rsvp != null) {
            switch (rsvp) {
                case "Yes":
                case "yes":
                case "YES":
                    return YES;
                case "No":
                case "no":
                case "NO":
                    return NO;
                case "No response yet":
                    return NO_RESPONSE;
                case "Maybe":
                case "maybe":
                case "Abstain":
                case "abstain":
                    return MAYBE;
                case "Invalid RSVP":
                    return INVALID_RESPONSE;
            }
        }
        return EventRSVPResponse.INVALID_RESPONSE;
    }

    public String getReadableString() {
        switch(this) {
            case YES:
                return "Yes";
            case NO:
                return "No";
            case NO_RESPONSE:
                return "No response";
            case MAYBE:
                return "Abstain";
        }
        return "Invalid response";
    }

}
