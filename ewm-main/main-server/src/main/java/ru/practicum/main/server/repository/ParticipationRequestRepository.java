package ru.practicum.main.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.main.server.model.ParticipationRequest;
import ru.practicum.main.server.model.ParticipationRequest.RequestStatus;

import java.util.List;
import java.util.Optional;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    List<ParticipationRequest> findAllByEventId(Long eventId);

    List<ParticipationRequest> findAllByRequesterId(Long userId);

    Optional<ParticipationRequest> findByEventIdAndRequesterId(Long eventId, Long userId);

    Long countByEventIdAndStatus(Long eventId, RequestStatus status);

    boolean existsByEventIdAndRequesterId(Long eventId, Long userId);

    @Query("select pr.event.id as eventId, count(pr.id) as confirmedRequests " +
            "from ParticipationRequest pr " +
            "where pr.event.id in :eventIds and pr.status = :status " +
            "group by pr.event.id")
    List<EventConfirmedCount> countConfirmedRequestsByEventIds(@Param("eventIds") List<Long> eventIds,
                                                               @Param("status") RequestStatus status);

    interface EventConfirmedCount {
        Long getEventId();

        Long getConfirmedRequests();
    }
}