package za.org.grassroot.integration.socialmedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString @NoArgsConstructor
public class FacebookAccount {

    private String pageName;

    private String pageId;

    @JsonProperty("pageName")
    public String getPageName() {
        return pageName;
    }

    @JsonProperty("page_name")
    public void setPageName(String pageName) {
        this.pageName = pageName;
    }

    @JsonProperty("pageId")
    public String getPageId() {
        return pageId;
    }

    @JsonProperty("page_id")
    public void setPageId(String pageId) {
        this.pageId = pageId;
    }
}
