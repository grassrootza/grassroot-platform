package za.org.grassroot.core.dto;

import java.io.Serializable;

/**
 * Created by aakilomar on 10/25/15.
 */
public class EventWithTotals implements Serializable {

    private EventDTO eventDTO;
    private ResponseTotalsDTO responseTotalsDTO;

    public EventWithTotals(EventDTO eventDTO, ResponseTotalsDTO responseTotalsDTO) {
        this.eventDTO = eventDTO;
        this.responseTotalsDTO = responseTotalsDTO;
    }

    public EventDTO getEventDTO() {
        return eventDTO;
    }

    public void setEventDTO(EventDTO eventDTO) {
        this.eventDTO = eventDTO;
    }

    public ResponseTotalsDTO getResponseTotalsDTO() {
        return responseTotalsDTO;
    }

    public void setResponseTotalsDTO(ResponseTotalsDTO rsvpTotalsDTO) {
        this.responseTotalsDTO = responseTotalsDTO;
    }

    @Override
    public String toString() {
        return "EventWithTotals{" +
                "eventDTO=" + eventDTO +
                ", rsvpTotalsDTO=" + responseTotalsDTO +
                '}';
    }
}
