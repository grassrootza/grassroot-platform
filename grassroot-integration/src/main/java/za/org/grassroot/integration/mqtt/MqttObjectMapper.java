package za.org.grassroot.integration.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;

/**
 * Created by luke on 2016/11/22.
 */
@Component
@ConditionalOnProperty(name = "mqtt.connection.enabled", havingValue = "true",  matchIfMissing = false)
public class MqttObjectMapper extends ObjectMapper {

    public MqttObjectMapper() {
        setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"));
        SimpleModule ldtModule = new SimpleModule();
        ldtModule.addSerializer(LocalDateTime.class, new LDTSerializer());
        ldtModule.addDeserializer(LocalDateTime.class, new LDTDeserializer());
        registerModule(ldtModule);
    }

}
