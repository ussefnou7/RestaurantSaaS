package com.smart.restaurant_saas.media;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaDeletionQueueRepository extends JpaRepository<MediaDeletionQueueEntry, Long> {

    List<MediaDeletionQueueEntry> findByOrderByIdAsc(Limit limit);
}
