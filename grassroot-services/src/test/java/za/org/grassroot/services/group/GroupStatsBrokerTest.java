package za.org.grassroot.services.group;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.google.common.base.CharMatcher.any;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static za.org.grassroot.core.domain.group.GroupJoinMethod.*;
import static za.org.grassroot.core.enums.Province.*;

@Slf4j
@RunWith(SpringRunner.class)
public class GroupStatsBrokerTest {

    @Mock
    private GroupLogRepository groupLogRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;
    @Mock
    private Element element;

    private GroupStatsBroker groupStatsBroker;

    @Before
    public void setUp() {
        given(cacheManager.getCache(anyString())).willReturn(cache);
        given(cache.get(any())).willReturn(element);
        groupStatsBroker = new GroupStatsBrokerImpl(groupLogRepository,groupRepository,membershipRepository,userRepository,cacheManager);
    }

    @Test
    public void testGetProvincesStats_ShouldReturnUnknownProvinceWhenUserProvinceIsNotKnown() {
        //when province isn't specified for a user, then repo defaults to unspecified
        Object[][] repositoryProvinceStats = {{"unspecified", new Long(10)}};
        List<Object[]> groupRepositoryProvinceStats = Arrays.asList(repositoryProvinceStats);
        given(groupRepository.getGroupProvinceStats(anyString())).willReturn(groupRepositoryProvinceStats);
        Map<String, Integer> mapProvinceStats = groupStatsBroker.getProvincesStats("groupUuid");
        mapProvinceStats.forEach((province, size) -> {
            assertEquals("UNKNOWN", province);
            assertEquals((Integer) 10, size);
        });
    }

    @Test
    public void testGetProvincesStats_ShouldReturnSameNumberOfEntriesAsProvincesForUsersInGroup() {
        Object[][] repositoryProvinceStats = {{ZA_GP, 3L},{ZA_LP, 5L},{ZA_EC,23L}};
        List<Object[]> groupRepositoryProvinceStats = Arrays.asList(repositoryProvinceStats);
        given(groupRepository.getGroupProvinceStats(anyString())).willReturn(groupRepositoryProvinceStats);
        Map<String, Integer> mapProvinceStats = groupStatsBroker.getProvincesStats("groupUuid");
        assertEquals(3, mapProvinceStats.size());
    }

    @Test
    public void testGetProvincesStats_ShouldReturnProvinceUnknownWhenProvinceIsNull() {
        Object[][] repositoryProvinceStats = {{null, 3L}};
        List<Object[]> groupRepositoryProvinceStats = Arrays.asList(repositoryProvinceStats);
        given(groupRepository.getGroupProvinceStats(anyString())).willReturn(groupRepositoryProvinceStats);
        Map<String, Integer> mapProvinceStats = groupStatsBroker.getProvincesStats("groupUuid");
        mapProvinceStats.forEach((province, size) -> {
            assertEquals("UNKNOWN", province);
            assertEquals((Integer) 3, size);
        });
    }

    @Test
    public void testGetProvincesStats_ShouldReturnProvinceSizeZeroWhenProvinceSizeIsNull() {
        Object[][] repositoryProvinceStats = {{ZA_LP, null}};
        List<Object[]> groupRepositoryProvinceStats = Arrays.asList(repositoryProvinceStats);
        given(groupRepository.getGroupProvinceStats(anyString())).willReturn(groupRepositoryProvinceStats);
        Map<String, Integer> mapProvinceStats = groupStatsBroker.getProvincesStats("groupUuid");
        mapProvinceStats.forEach((province, size) -> {
            assertEquals(ZA_LP.name(), province);
            assertEquals((Integer) 0, size);
        });
    }

    @Test
    public void testGetSourcesStats_ShouldReturnUnknownSourceWhenMemberSourceIsNotKnown() {
        Object[][] repositorySourceStats = {{"unspecified", 4L}};
        List<Object[]> groupRepositorySourceStats = Arrays.asList(repositorySourceStats);
        given(groupRepository.getGroupSourcesStats(anyString())).willReturn(groupRepositorySourceStats);
        Map<String, Integer> mapSourceStats = groupStatsBroker.getSourcesStats("groupUuid");
        mapSourceStats.forEach((source, size) -> {
            assertEquals("UNKNOWN", source);
            assertEquals((Integer) 4, size);
        });
    }

    @Test
    public void testGetSourcesStats_ShouldReturnSameNumberOfEntriesAsSourcesForMembersInGroup() {
        Object[][] repositorySourceStats = {{ADDED_BY_OTHER_MEMBER, 5L},{ADDED_AT_CREATION, 2L},{ADDED_BY_SYS_ADMIN, 10L}};
        List<Object[]> groupRepositorySourceStats = Arrays.asList(repositorySourceStats);
        given(groupRepository.getGroupSourcesStats(anyString())).willReturn(groupRepositorySourceStats);
        Map<String, Integer> mapSourceStats = groupStatsBroker.getSourcesStats("groupUuid");
        assertEquals(3, mapSourceStats.size());
    }

    @Test
    public void testGetSourcesStats_ShouldReturnCountOfSourcePerGroup() {
        Object[][] repositorySourceStats = {{ADDED_BY_OTHER_MEMBER, 2L}};
        List<Object[]> groupRepositorySourceStats = Arrays.asList(repositorySourceStats);
        given(groupRepository.getGroupSourcesStats(anyString())).willReturn(groupRepositorySourceStats);
        Map<String, Integer> mapSourceStats = groupStatsBroker.getSourcesStats("groupUuid");
        mapSourceStats.forEach((source, size) -> {
            assertEquals(ADDED_BY_OTHER_MEMBER.name(), source);
            assertEquals((Integer) 2, size);
        });
    }

    @Test
    public void testGetSourcesStats_ShouldReturnProvinceUnknownWhenProvinceIsNull() {
        Object[][] repositorySourceStats = {{"unspecified", 3L}};
        List<Object[]> groupRepositorySourceStats = Arrays.asList(repositorySourceStats);
        given(groupRepository.getGroupSourcesStats(anyString())).willReturn(groupRepositorySourceStats);
        Map<String, Integer> mapSourceStats = groupStatsBroker.getSourcesStats("groupUuid");
        mapSourceStats.forEach((source, size) -> {
            assertEquals("UNKNOWN", source);
            assertEquals((Integer) 3, size);
        });
    }

    @Test
    public void testGetSourcesStats_ShouldReturnProvinceSizeZeroWhenProvinceSizeIsNull() {
        Object[][] repositorySourceStats = {{ADDED_BY_SYS_ADMIN, null}};
        List<Object[]> groupRepositorySourceStats = Arrays.asList(repositorySourceStats);
        given(groupRepository.getGroupSourcesStats(anyString())).willReturn(groupRepositorySourceStats);
        Map<String, Integer> mapSourceStats = groupStatsBroker.getSourcesStats("groupUuid");
        mapSourceStats.forEach((source, size) -> {
            assertEquals(ADDED_BY_SYS_ADMIN.name(), source);
            assertEquals((Integer) 0, size);
        });
    }
}
