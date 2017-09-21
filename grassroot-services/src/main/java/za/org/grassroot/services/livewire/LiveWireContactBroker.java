package za.org.grassroot.services.livewire;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.dto.LiveWireContactDTO;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.util.List;

public interface LiveWireContactBroker {

    @PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
    Page<LiveWireContactDTO> loadLiveWireContacts(String userUid, String filterTerm, Pageable pageable);

    @PreAuthorize("hasRole('ROLE_LIVEWIRE_USER')")
    List<LiveWireContactDTO> loadLiveWireContacts(String userUid);

    List<LiveWireContactDTO> fetchLiveWireContactsNearby(String queryingUserUid, GeoLocation location, Integer radius);

    // set boolean to false to revoke
    void updateUserLiveWireContactStatus(String userUid, boolean addingPermission, UserInterfaceType interfaceType);

    void trackLocationForLiveWireContact(String userUid, UserInterfaceType type);

}
