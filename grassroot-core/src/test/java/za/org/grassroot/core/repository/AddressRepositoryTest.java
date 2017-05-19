package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.User;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by paballo on 2016/07/21.
 */


@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
@Transactional
public class AddressRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    private String testHouseNumber = "44";
    private String testStreetName = "Stanley street";
    private String testTown = "Milpark";


    @Test
    public void shouldFindByResident() throws Exception{

     User user = userRepository.save(new User("0833203013"));
        addressRepository.save(new Address(user, testHouseNumber,testStreetName,testTown, true));
        Address address = addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);
        assertNotNull(address);
        assertNotNull(address.getUid());
        assertNotNull(address.getCreatedDateTime());
        assertEquals(address.getHouse(),testHouseNumber);
        assertEquals(address.getStreet(),testStreetName);
        assertEquals(address.getNeighbourhood(),testTown);

    }

}
