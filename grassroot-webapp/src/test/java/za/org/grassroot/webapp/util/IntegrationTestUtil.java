package za.org.grassroot.webapp.util;


import org.apache.commons.beanutils.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Created by paballo on 2016/01/20.
 */
import org.springframework.http.MediaType;
import java.nio.charset.Charset;

public class IntegrationTestUtil {

    public static final MediaType APPLICATION_XML = new MediaType(MediaType.APPLICATION_XML.getType(),
            MediaType.APPLICATION_XML.getSubtype(), Charset.forName("utf8"));

}

