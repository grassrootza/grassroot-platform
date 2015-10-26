package za.org.grassroot.core.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Created by aakilomar on 9/21/15.
 */
public class RSVPTotalsDTO implements Serializable {

    private int yes;
    private int no;
    private int maybe;
    private int invalid;
    private int numberOfUsers;

    public RSVPTotalsDTO() {
    }

    public RSVPTotalsDTO(List<Object[]> listFields) {
        Object[] fields = listFields.get(0);
        this.yes = (fields[0] == null) ? 0 : Integer.parseInt(fields[0].toString());
        this.no = (fields[1] == null) ? 0 : Integer.parseInt(fields[1].toString());
        this.maybe = (fields[2] == null) ? 0 : Integer.parseInt(fields[2].toString());
        this.invalid = (fields[3] == null) ? 0 : Integer.parseInt(fields[3].toString());
        this.numberOfUsers = (fields[4] == null) ? 0 : Integer.parseInt(fields[4].toString());

    }

    public int getNumberNoRSVP() {
        return numberOfUsers - (yes + no + maybe + invalid);
    }

    public void add(RSVPTotalsDTO other) {
        this.yes += other.getYes();
        this.no += other.getNo();
        this.maybe += other.getMaybe();
        this.invalid += other.getInvalid();
        this.numberOfUsers += other.getNumberOfUsers();
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
        return "RSVPTotalsDTO{" +
                "yes=" + yes +
                ", no=" + no +
                ", maybe=" + maybe +
                ", invalid=" + invalid +
                ", numberOfUsers=" + numberOfUsers +
                ", numberNoRSVP=" + getNumberNoRSVP() +
                '}';
    }
}
