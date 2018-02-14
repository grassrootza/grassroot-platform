package za.org.grassroot.integration.socialmedia;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

@Getter @Setter @NoArgsConstructor
public class ManagedPagesResponse extends ServiceResponse {

    List<ManagedPage> managedPages;

    public ManagedPagesResponse(boolean isUserConnectionValid, List<ManagedPage> managedPages) {
        super(isUserConnectionValid);
        this.managedPages = managedPages;
    }

    public Optional<String> getPageNameForId(String pageId) {
        return managedPages.stream().filter(managedPage -> managedPage.getProviderUserId().equals(pageId))
                .findFirst().map(ManagedPage::getDisplayName);
    }

}
