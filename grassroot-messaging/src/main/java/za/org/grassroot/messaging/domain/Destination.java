package za.org.grassroot.messaging.domain;

import java.util.ArrayList;
import java.util.List;

/**
 *
 *  Represents the destination of the message
 *
 * @author Lesetse Kimwaga
 */
public class Destination {

    private List<String> toAddresses;

    public Destination(List<String> toAddresses) {
        this.toAddresses = toAddresses;
    }

    public List<String> getToAddresses() {
        return toAddresses;
    }

    public void setToAddresses(List<String> toAddresses) {
        this.toAddresses = toAddresses;
    }

    public void addToAddress(String toAddress) { this.toAddresses.add(toAddress); }

    public Destination withToAddresses(String... toAddresses) {
        if (getToAddresses() == null) setToAddresses(new java.util.ArrayList<String>(toAddresses.length));
        for (String value : toAddresses) {
            getToAddresses().add(value);
        }
        return this;
    }

    /*
    Constructors
     */

    public Destination() {
        toAddresses = new ArrayList<>();
    }

}
