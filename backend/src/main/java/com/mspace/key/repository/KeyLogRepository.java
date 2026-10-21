package com.mspace.key.repository;

import com.mspace.key.domain.KeyLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyLogRepository extends JpaRepository<KeyLog, Long> {

    List<KeyLog> findByReservationIdOrderByOccurredAtAscIdAsc(Long reservationId);
}
