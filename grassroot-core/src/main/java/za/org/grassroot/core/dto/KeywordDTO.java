package za.org.grassroot.core.dto;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Created by paballo on 2016/08/17.
 */
@Entity
public class KeywordDTO {

    @Id
    private String keyword;
    private Integer frequency;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }
}
