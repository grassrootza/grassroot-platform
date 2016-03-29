package za.org.grassroot.webapp.model.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by aakil.
 */
public class GroupViewNodeSql {

    private static final Logger log = LoggerFactory.getLogger(GroupViewNodeSql.class);

    private List<GroupViewNodeSql> subgroups;

    private String groupName;
    private Integer level;
    private Long parentId;

    /*
    Constructor
     */

    public GroupViewNodeSql(String groupName, Integer level, Long parentId) {
        this.groupName = groupName;
        this.level = level;
        this.parentId = parentId;
        subgroups = new ArrayList<>();
    }

    public GroupViewNodeSql() {
        subgroups = new ArrayList<>();

    }

    public boolean isTerminal() {
        return (subgroups.size() == 0);
    }
    public List<GroupViewNodeSql> getSubgroups() {
        return subgroups;
    }

    public void setSubgroups(List<GroupViewNodeSql> subgroups) {
        this.subgroups = subgroups;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
