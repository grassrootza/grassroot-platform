package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.Address;
import za.org.grassroot.core.domain.geo.Address_;

/**
 * Created by luke on 2017/05/12.
 */
public final class AddressSpecifications {

    public static Specification<Address> forUser(User user) {
        return (root, query, cb) -> cb.equal(root.get(Address_.resident), user);
    }

    public static Specification<Address> matchesStreetArea(String house, String street, String area) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get(Address_.house), house),
                cb.equal(root.get(Address_.street), street),
                cb.equal(root.get(Address_.neighbourhood), area));
    }

    public static Specification<Address> hasLocationTownOrPostalCode() {
        return (root, query, cb) -> cb.or(
                cb.isNotNull(root.get(Address_.location)),
                cb.isNotNull(root.get(Address_.postalCode)),
                cb.isNotNull(root.get(Address_.townOrCity))
        );
    }

}
