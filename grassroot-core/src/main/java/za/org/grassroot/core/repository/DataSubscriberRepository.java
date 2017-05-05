package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.DataSubscriber;

/**
 * Created by luke on 2017/05/05.
 */
public interface DataSubscriberRepository extends JpaRepository<DataSubscriber, Long>,
        JpaSpecificationExecutor<DataSubscriber> {

    DataSubscriber findOneByUid(String uid);

}
