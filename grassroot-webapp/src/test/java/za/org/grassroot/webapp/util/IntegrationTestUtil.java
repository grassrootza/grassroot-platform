package za.org.grassroot.webapp.util;


import org.springframework.http.MediaType;

import java.nio.charset.Charset;

/**
 * Created by paballo on 2016/01/20.
 */

public class IntegrationTestUtil {

    public static final MediaType APPLICATION_XML = new MediaType(MediaType.APPLICATION_XML.getType(),
            MediaType.APPLICATION_XML.getSubtype(), Charset.forName("utf8"));

}

