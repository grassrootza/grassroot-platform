package za.org.grassroot.integration.mqtt;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import za.org.grassroot.integration.utils.Constants;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Created by luke on 2016/11/22.
 */
public class LDTDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return LocalDateTime.parse(p.getText(), Constants.CHAT_TIME_FORMAT);
    }
}
