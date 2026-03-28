package mtech.swe5006.peerconnect.data.sql;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TutoringClassRepository extends JpaRepository<TutoringClass, UUID> {
    List<TutoringClass> findAllByOrderByCreatedAtDesc();
    List<TutoringClass> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);
}