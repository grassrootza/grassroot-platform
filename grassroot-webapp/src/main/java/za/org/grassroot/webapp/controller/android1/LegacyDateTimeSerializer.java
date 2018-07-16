package za.org.grassroot.webapp.controller.android1;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class LegacyDateTimeSerializer extends JsonSerializer<LocalDateTime> {

    @Override
    public void serialize(LocalDateTime localDateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        // this is super painful but seems no other way
        Map<String, String> properties = new HashMap<>();
        properties.put("year", "" + localDateTime.getYear());
        properties.put("monthValue", "" + localDateTime.getMonthValue());
        properties.put("dayOfMonth", "" + localDateTime.getDayOfMonth());
        properties.put("hour", "" + localDateTime.getHour());
        properties.put("minute", "" + localDateTime.getMinute());
        properties.put("second", "" + localDateTime.getSecond());
        log.debug("Serialized properties: {}", properties);
        jsonGenerator.writeObject(properties);
    }
}
