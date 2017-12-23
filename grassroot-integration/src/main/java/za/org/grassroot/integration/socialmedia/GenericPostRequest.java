package za.org.grassroot.integration.socialmedia;

import lombok.Getter;

@Getter
public class GenericPostRequest {

    private String pageId;
    private String message;

    private String linkUrl;
    private String name;
    private String caption;
    private String description;

    private String pictureUrl;

    protected GenericPostRequest(FBPostBuilder facebookPost, String imageUrlBase) {
        this.pageId = facebookPost.getFacebookPageId();
        this.message = facebookPost.getMessage();
        this.linkUrl = facebookPost.getLinkUrl();
        this.name = facebookPost.getLinkName();

        this.caption = facebookPost.getImageCaption();

        this.pictureUrl = imageUrlBase + facebookPost.getImageKey();
    }

    protected GenericPostRequest(TwitterPostBuilder twitterPost, String imageUrlBase) {
        this.message = twitterPost.getMessage();
        this.pictureUrl = imageUrlBase + twitterPost.getImageKey();
    }

}
