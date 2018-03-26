package za.org.grassroot.integration;

import za.org.grassroot.core.domain.media.MediaFunction;

/**
 * Created by luke on 2017/06/26.
 */
public interface UrlShortener {

    String shortenImageUrl(MediaFunction mediaFunction, String imageUrl);

    String shortenJoinUrls(String joinUrl);

}
