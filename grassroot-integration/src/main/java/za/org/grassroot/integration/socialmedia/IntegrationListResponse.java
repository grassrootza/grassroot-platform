package za.org.grassroot.integration.socialmedia;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter @Setter
public class IntegrationListResponse {

    Map<String, ManagedPagesResponse> currentIntegrations = new HashMap<>();

    public void addIntegration(String providerId, ManagedPagesResponse managedPages) {
        this.currentIntegrations.put(providerId, managedPages);
    }

}
