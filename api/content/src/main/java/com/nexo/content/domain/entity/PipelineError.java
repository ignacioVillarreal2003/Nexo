package com.nexo.content.domain.entity;

import com.nexo.content.domain.entity.enums.PipelineStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_errors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PipelineError {

    private static final int MAX_MESSAGE = 4000;

    @Id
    private UUID id;

    @Column(name = "run_id")
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 30)
    private PipelineStage stage;

    @Column(name = "source", length = 300)
    private String source;

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "message", nullable = false, length = MAX_MESSAGE)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PipelineError of(UUID runId, PipelineStage stage, String source, UUID topicId, String message) {
        PipelineError error = new PipelineError();
        error.id = UUID.randomUUID();
        error.runId = runId;
        error.stage = stage;
        error.source = source == null || source.length() <= 300 ? source : source.substring(0, 300);
        error.topicId = topicId;
        String text = message == null ? "" : message;
        error.message = text.length() <= MAX_MESSAGE ? text : text.substring(0, MAX_MESSAGE);
        error.createdAt = Instant.now();
        return error;
    }
}
