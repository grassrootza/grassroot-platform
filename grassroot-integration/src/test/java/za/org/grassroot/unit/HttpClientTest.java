package za.org.grassroot.unit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;
import za.org.grassroot.integration.HttpConfig;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * Created by luke on 2016/05/17.
 */
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = { HttpConfig.class })
@RunWith(SpringJUnit4ClassRunner.class)
public class HttpClientTest {

    private static final String url = "http://httpbin.org/status/200";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AsyncRestTemplate asyncRestTemplate;

    @Test
    public void sync() {

        ResponseEntity<Map> entity = restTemplate.getForEntity(url, Map.class);

        assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
    }

    @Test
    public void asyncWithFuture() throws InterruptedException, ExecutionException {
        Future<ResponseEntity<Map>> future = asyncRestTemplate.getForEntity(url, Map.class);
        while (!future.isDone()) {
            TimeUnit.MILLISECONDS.sleep(100);
        }
        ResponseEntity<Map> entity = future.get();
        assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
        // assertEquals("grassrootza", entity.getBody().get("login"));
    }
}
