package za.org.grassroot.webapp.util;


import org.apache.commons.beanutils.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Created by paballo on 2016/01/20.
 */
public abstract class TestUtil {

    public static MockHttpServletRequestBuilder postForm(String url,
                                                         Object modelAttribute, String... propertyPaths) {

        try {
            MockHttpServletRequestBuilder form = post(url).characterEncoding(
                    "UTF-8").contentType(MediaType.APPLICATION_FORM_URLENCODED);


            for (String path : propertyPaths) {
                form.param(path, BeanUtils.getProperty(modelAttribute, path));
            }

            return form;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
