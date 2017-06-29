package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query(value = "select distinct unnest(access_users) from data_subscriber " +
            "where active = true", nativeQuery = true)
    List<String> userUidsOfDataSubscriberUsers();

    @Query(value = "select distinct unnest(access_users) from data_subscriber " +
            "where active = true and can_release = true", nativeQuery = true)
    List<String> fetchUserUidsOfReviewingUsers();

    @Query(value = "select * from data_subscriber where ?1 = ANY(access_users)", nativeQuery = true)
    List<DataSubscriber> findSubscriberHoldingUser(String userUid);

    @Modifying
    @Query(value = "UPDATE data_subscriber SET push_emails = array_remove(push_emails, CAST(?1 as text))", nativeQuery = true)
    void removeEmailFromAllSubscribers(String emailAddress);

}