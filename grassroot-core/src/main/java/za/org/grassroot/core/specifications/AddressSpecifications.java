package za.org.grassroot.core.specifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.Address_;
import za.org.grassroot.core.domain.User;

/**
 * Created by luke on 2017/05/12.
 */
public final class AddressSpecifications {

    public static Specification<Address> forUser(User user) {
        return (root, query, cb) -> cb.equal(root.get(Address_.resident), user);
    }

    public static Specification<Address> isPrimary(boolean isPrimary) {
        return (root, query, cb) -> cb.equal(root.get(Address_.primary), isPrimary);
    }

    public static Specification<Address> matchesStreetArea(String house, String street, String area) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get(Address_.house), house),
                cb.equal(root.get(Address_.street), street),
                cb.equal(root.get(Address_.neighbourhood), area));
    }

}
