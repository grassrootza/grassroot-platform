package za.org.grassroot.core.dto;

import java.io.Serializable;

/**
 * Created by aakilomar on 10/25/15.
 */
public class EventWithTotals implements Serializable {

    private EventDTO eventDTO;
    private RSVPTotalsDTO rsvpTotalsDTO;

    public EventWithTotals(EventDTO eventDTO, RSVPTotalsDTO rsvpTotalsDTO) {
        this.eventDTO = eventDTO;
        this.rsvpTotalsDTO = rsvpTotalsDTO;
    }

    public EventDTO getEventDTO() {
        return eventDTO;
    }

    public void setEventDTO(EventDTO eventDTO) {
        this.eventDTO = eventDTO;
    }

    public RSVPTotalsDTO getRsvpTotalsDTO() {
        return rsvpTotalsDTO;
    }

    public void setRsvpTotalsDTO(RSVPTotalsDTO rsvpTotalsDTO) {
        this.rsvpTotalsDTO = rsvpTotalsDTO;
    }

    @Override
    public String toString() {
        return "EventWithTotals{" +
                "eventDTO=" + eventDTO +
                ", rsvpTotalsDTO=" + rsvpTotalsDTO +
                '}';
    }
}
