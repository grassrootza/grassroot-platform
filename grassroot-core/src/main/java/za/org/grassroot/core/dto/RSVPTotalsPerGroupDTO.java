package za.org.grassroot.core.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Created by aakilomar on 9/21/15.
 */
public class RSVPTotalsPerGroupDTO implements Serializable {

    private Long groupId;
    private String groupName;
    private int yes;
    private int no;
    private int numberOfUsers;

    public RSVPTotalsPerGroupDTO() {
    }

    public RSVPTotalsPerGroupDTO(Object[] fields) {
        this.groupId = (fields[0] == null) ? 0 : Long.parseLong(fields[0].toString());
        this.groupName = (fields[1] == null) ? "" : fields[1].toString();
        this.yes = (fields[2] == null) ? 0 : Integer.parseInt(fields[2].toString());
        this.no = (fields[3] == null) ? 0 : Integer.parseInt(fields[3].toString());
        this.numberOfUsers = (fields[4] == null) ? 0 : Integer.parseInt(fields[4].toString());

    }

    public int getNumberNoRSVP() {
        return numberOfUsers - (yes + no);
    }

    public int getYesPercentage() {
        int yesPercentage = 0;
        try {
            yesPercentage =  Math.round(100 * yes / numberOfUsers);
        } catch (Exception e) {

        }
        return yesPercentage;
    }

    public int getNoPercentage() {
        int noPercentage = 0;
        try {
            noPercentage =  Math.round(100 * no / numberOfUsers);
        } catch (Exception e) {

        }
        return noPercentage;
    }

    // getters and setters

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


    public int getNumberOfUsers() {
        return numberOfUsers;
    }

    public void setNumberOfUsers(int numberOfUsers) {
        this.numberOfUsers = numberOfUsers;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public String toString() {
        return "RSVPTotalsPerGroupDTO{" +
                "groupId=" + groupId +
                ", groupName='" + groupName + '\'' +
                ", yes=" + yes +
                ", no=" + no +
                ", numberOfUsers=" + numberOfUsers +
                '}';
    }
}
