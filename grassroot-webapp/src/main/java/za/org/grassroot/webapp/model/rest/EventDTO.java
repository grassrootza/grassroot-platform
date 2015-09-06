package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.Event;

import java.util.Date;

/**
 * Created by aakilomar on 9/6/15.
 */
public class EventDTO {

    private Long id;
    private String name;
    private String location;
    private Date date;

    public EventDTO(Event event) {
        this.id = event.getId();
        this.name = event.getName();
        this.location = event.getEventLocation();
        this.date = event.getEventStartDateTime();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "EventDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", location='" + location + '\'' +
                ", date=" + date +
                '}';
    }
}
