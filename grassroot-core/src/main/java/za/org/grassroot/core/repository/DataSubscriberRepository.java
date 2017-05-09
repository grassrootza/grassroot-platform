package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.livewire.DataSubscriber;

import java.util.List;

/**
 * Created by luke on 2017/05/05.
 */
public interface DataSubscriberRepository extends JpaRepository<DataSubscriber, Long>,
        JpaSpecificationExecutor<DataSubscriber> {

    DataSubscriber findOneByUid(String uid);

    List<DataSubscriber> findByActiveTrue(Sort sort);

    @Query(value = "select distinct unnest(push_emails) from data_subscriber " +
            "where active = true", nativeQuery = true)
    List<String> findAllActiveSubscriberPushEmails();

}
