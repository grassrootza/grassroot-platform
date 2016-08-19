package za.org.grassroot.core.dto;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Created by paballo on 2016/08/17.
 */
@Entity
public class KeywordDTO {

    @Id
    private String keyword;
    @Column(name = "group_name_count")
    private Integer groupNameCount ;
    @Column(name = "meeting_name_count")
    private Integer meetingNameCount;
    @Column(name = "vote_name_count")
    private Integer voteNameCount;
    @Column(name = "todo_count")
    private Integer todoCount;
    @Column(name = "total_occurence")
    private Integer totalOccurence;

    public String getKeyword() {
        return keyword;
    }

    public Integer getGroupNameCount() {
        return groupNameCount != null ? groupNameCount : Integer.valueOf(0);
    }

    public Integer getMeetingNameCount() {
        return meetingNameCount != null ? meetingNameCount : Integer.valueOf(0);
    }

    public Integer getVoteNameCount() {return voteNameCount != null ? voteNameCount : Integer.valueOf(0); }

    public Integer getTodoCount() {
        return todoCount != null ? todoCount : Integer.valueOf(0);
    }

    public Integer getTotalOccurence() {
        return totalOccurence;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }


}
