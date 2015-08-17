package za.org.grassroot.meeting_organizer.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.persistence.*;

//todo: add validation to all model classes
//todo: use java 8 date and time types and a JPA converter instead of Timestamp type
//todo: createdDateTime should be read-only -  the database should insert this automatically
//todo: use field annotations rather than getter annotations because then all the annotations will be closer together
//todo: make these classes immutable - all args constructor and no setters
//todo: id and createdDateTime fields should not be insertable or updatable
//todo: extract base class for all entities that has id and createdDateTime

@Entity
@Table(name = "\"user\"")  //table name needs to be quoted in SQL because 'user' is a reserved keyword
@EqualsAndHashCode
@ToString
public class User {
    private String phoneNumber;
    private String displayName;
    private Integer id;
    private Timestamp createdDateTime;

    private List<Group> groupsPartOf;

    @Basic
    @Column(name = "phone_number", nullable = false, length = 20)
    public String getPhoneNumber() { return phoneNumber; }

    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    @Basic
    @Column(name = "display_name", nullable = true, length = 70) // allowing this to be nullable as might not be set
    public String getDisplayName() { return displayName; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Basic
    @Column(name = "created_date_time", insertable = false, updatable = false)
    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    @OneToMany(mappedBy = "user")
    private List<Group> groupsCreated;

    @OneToMany(mappedBy = "user")
    private List<Event> eventsCreated;

    @ManyToMany(cascade = CascadeType.ALL, mappedBy = "groupMembers") // not sure which cascade type is going to be best
    public List<Group> getGroupsPartOf() { return groupsPartOf; }
    public void setGroupsPartOf(List<Group> groupsPartOf) { this.groupsPartOf = groupsPartOf; }

    /**
     * Two methods to check if this user needs to rename themselves, and/or name a group--probably move to service
     * UX logic: If user accesses through USSD, on first use, don't want to ask for name, but do thereafter
     * Similarly, for Group, don't want to slow down to ask for group when we start, but want to come back and get it
     */

    public boolean needsToRenameSelf(Integer timeLimit) {
        if (hasName()) return false;
        Timestamp minutesAgo = new Timestamp(System.currentTimeMillis() - (timeLimit * 60 * 1000));
        if (createdDateTime == null || createdDateTime.after(minutesAgo))
            return false;
        return true;
    }

    public boolean needsToRenameGroup() { // placeholder
        return false;
    }

    /**
     * Inserting string functions to handle phone numbers here, for the moment
     */

    public boolean hasName() {
        return (displayName != null && displayName.trim().length() > 0);
    }

    public String getName(String unknownPrefix) {
        if (displayName != null && displayName.trim().length() > 0) {
            return displayName;
        } else if (unknownPrefix.trim().length() == 0){
            return invertPhoneNumber(phoneNumber);
        } else {
            return unknownPrefix + " (" + invertPhoneNumber(phoneNumber) + ")";
        }
    }

    public static String convertPhoneNumber(String inputString) {

        // todo: decide on our preferred string format, for now keeping it at for 27 (not discarding info)
        // todo: add error handling to this.
        // todo: consider using Guava libraries, or another, for when we get to tricky user input
        // todo: put this in a wrapper class for a bunch of auxiliary methods? think we'll use this a lot

        int codeLocation = inputString.indexOf("27");
        boolean hasCountryCode = (codeLocation >= 0 && codeLocation < 2); // allowing '1' for '+' and 2 for '00'
        if (hasCountryCode) {
            return inputString.substring(codeLocation);
        } else {
            String truncedNumber = (inputString.charAt(0) == '0') ? inputString.substring(1) : inputString;
            return "27" + truncedNumber;
        }
    }

    public static String invertPhoneNumber(String storedNumber) {

        // todo: handle error if number has gotten into database in incorrect format
        // todo: make this much faster, e.g., use a simple regex / split function?

        List<String> numComponents = new ArrayList<>();
        String prefix = String.join("", Arrays.asList("0", storedNumber.substring(2, 4)));
        String midnumbers, finalnumbers;

        try {
            midnumbers = storedNumber.substring(4,7);
            finalnumbers = storedNumber.substring(7,11);
        } catch (Exception e) { // in case the string doesn't have enough digits ...
            midnumbers = storedNumber.substring(4);
            finalnumbers = "";
        }

        return String.join(" ", Arrays.asList(prefix, midnumbers, finalnumbers));

    }

}
