package ru.practicum.main.server.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.main.server.model.Event;
import ru.practicum.main.server.model.Event.EventState;
import ru.practicum.main.server.model.ParticipationRequest.RequestStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findAllByInitiatorId(Long userId, Pageable pageable);

    @Query("SELECT e FROM Event e " +
            "WHERE (:users IS NULL OR e.initiator.id IN :users) " +
            "AND (:states IS NULL OR e.state IN :states) " +
            "AND (:categories IS NULL OR e.category.id IN :categories) " +
            "AND (:rangeStart IS NULL OR e.eventDate >= :rangeStart) " +
            "AND (:rangeEnd IS NULL OR e.eventDate <= :rangeEnd)")
    List<Event> findEventsByAdmin(@Param("users") List<Long> users,
                                  @Param("states") List<EventState> states,
                                  @Param("categories") List<Long> categories,
                                  @Param("rangeStart") LocalDateTime rangeStart,
                                  @Param("rangeEnd") LocalDateTime rangeEnd,
                                  Pageable pageable);

    @Query("SELECT e FROM Event e " +
            "WHERE e.state = :publishedState " +
            "AND (:text = '' OR (upper(e.annotation) LIKE upper(concat('%', :text, '%')) " +
            "OR upper(e.description) LIKE upper(concat('%', :text, '%')))) " +
            "AND (:categoriesEmpty = true OR e.category.id IN :categories) " +
            "AND (:paid IS NULL OR e.paid = :paid) " +
            "AND (e.eventDate BETWEEN :rangeStart AND :rangeEnd) " +
            "AND (:onlyAvailable = false OR e.participantLimit = 0 OR " +
            "(SELECT count(pr.id) FROM ParticipationRequest pr " +
            " WHERE pr.event.id = e.id AND pr.status = :confirmedStatus) < e.participantLimit)")
    List<Event> findPublishedEvents(@Param("text") String text,
                                    @Param("categories") List<Long> categories,
                                    @Param("categoriesEmpty") boolean categoriesEmpty,
                                    @Param("paid") Boolean paid,
                                    @Param("rangeStart") LocalDateTime rangeStart,
                                    @Param("rangeEnd") LocalDateTime rangeEnd,
                                    @Param("onlyAvailable") boolean onlyAvailable,
                                    @Param("publishedState") EventState publishedState,
                                    @Param("confirmedStatus") RequestStatus confirmedStatus,
                                    Pageable pageable);
}