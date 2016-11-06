package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.GroupChatMessageStats;

/**
 * Created by paballo on 2016/11/06.
 */


public interface GroupChatMessageStatsRepository extends JpaRepository<GroupChatMessageStats, Long>{

    GroupChatMessageStats findByUid(String messageUid);


}

