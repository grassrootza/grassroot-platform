package za.org.grassroot.integration.storage;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

/**
 * Created by luke on 2017/02/21.
 */
public class S3ClientFactory {

    private final AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
            .withRegion(Regions.EU_WEST_1)
            .withCredentials(new ProfileCredentialsProvider("s3images"));

    public AmazonS3 createClient() {
        return builder.build();
    }

}
