package za.org.grassroot.webapp.model.web;

import za.org.grassroot.webapp.enums.VoteType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2017/06/05.
 */
public class VoteWrapper extends EventWrapper {

    private VoteType type;
    private List<String> options;

    public static VoteWrapper makeEmpty() {
        VoteWrapper voteWrapper = new VoteWrapper();
        voteWrapper.options = new ArrayList<>();
        voteWrapper.type = VoteType.YES_NO;
        return voteWrapper;
    }

    public VoteType getType() {
        return type;
    }

    public void setType(VoteType type) {
        this.type = type;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    @Override
    public String toString() {
        return "VoteWrapper{" +
                "type=" + type +
                ", options=" + options +
                ", title='" + title + '\'' +
                '}';
    }
}
