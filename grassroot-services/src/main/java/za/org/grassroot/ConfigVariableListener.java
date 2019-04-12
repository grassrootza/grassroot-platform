package za.org.grassroot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.ConfigVariable;
import za.org.grassroot.core.events.AlterConfigVariableEvent;
import za.org.grassroot.core.repository.ConfigRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ConfigVariableListener implements ApplicationListener<AlterConfigVariableEvent> {

    protected Map<String, String> configVariables = new HashMap<>();

    private final ConfigRepository configRepository;

    public ConfigVariableListener(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    protected void setDefaults(Map<String, String> defaultMap) {
        defaultMap.forEach((key, defaultValue) -> configVariables.put(key, getValueOrPersistIfMissing(key, defaultValue)));
    }

    private String getValueOrPersistIfMissing(String key, String defaultValue) {
        Optional<ConfigVariable> persistedVar = configRepository.findOneByKey(key);
        if (persistedVar.isPresent())
            return persistedVar.get().getValue();

        log.info("System default config variable missing, persisting with key: {}, value: {}", key, defaultValue);
        configRepository.save(new ConfigVariable(key, defaultValue, "Created by system default"));
        return defaultValue;
    }

    protected String getConfigVariableValue(String key, String defaultValue) {
        return configRepository.findOneByKey(key).map(ConfigVariable::getValue).orElse(defaultValue);
    }

    @Override
    public void onApplicationEvent(AlterConfigVariableEvent event) {
        log.info("Received notice of some change in config variables: {}", event);
        if (!StringUtils.isEmpty(event.getKey())) {
            configRepository.findOneByKey(event.getKey()).ifPresent(this::updateConfig);
        }
    }

    private void updateConfig(ConfigVariable configVariable) {
        log.info("Updating config for applicable beans ...");

        if(configVariables.containsKey(configVariable.getKey())){
            configVariables.put(configVariable.getKey(), configVariable.getValue());
        }
    }

}
