package ru.practicum.stat.server.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "endpoint_hit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EndpointHit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String app;
    @Column(nullable = false)
    private String uri;
    @Column(nullable = false)
    private String ip;
    @Column(nullable = false)
    private LocalDateTime timestamp;
}
