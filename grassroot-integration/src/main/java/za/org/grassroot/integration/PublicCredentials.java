package za.org.grassroot.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by luke on 2017/05/22.
 */
public class PublicCredentials {

    private final String kuid;
    private final String b64UrlPublicKey;

    @JsonCreator
    public PublicCredentials(@JsonProperty("kuid") String kuid, @JsonProperty("b64UrlPublicKey") String b64UrlPublicKey) {
        this.kuid = kuid;
        this.b64UrlPublicKey = b64UrlPublicKey;
    }

    public String getKuid() {
        return kuid;
    }

    public String getB64UrlPublicKey() {
        return b64UrlPublicKey;
    }

    @Override
    public String toString() {
        return "PublicCredentials{" +
                "kuid='" + kuid + '\'' +
                ", b64UrlPublicKey='" + b64UrlPublicKey + '\'' +
                '}';
    }
}
