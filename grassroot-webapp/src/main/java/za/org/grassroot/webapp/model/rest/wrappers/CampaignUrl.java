package za.org.grassroot.webapp.model.rest.wrappers;


import java.io.Serializable;

public class CampaignUrl implements Serializable {

    private static final long serialVersionUID = 8025431563634000787L;
    private final String key;
    private final String url;

    public CampaignUrl(String key, String url){
        this.key = key;
        this.url = url;
    }

    public String getKey() {
        return key;
    }

    public String getUrl() {
        return url;
    }
}
