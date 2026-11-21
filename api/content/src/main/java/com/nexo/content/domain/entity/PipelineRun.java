package com.nexo.content.domain.entity;

import com.nexo.content.domain.entity.enums.RunStatus;
import com.nexo.content.domain.entity.enums.RunTrigger;
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

/**
 * Una ejecución de recopilación. El collector la abre y la cierra; los contadores de procesamiento
 * crecen de forma asíncrona mientras el servicio consume los documentos.
 */
@Entity
@Table(name = "pipeline_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PipelineRun {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private RunTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "topics_planned", nullable = false)
    private int topicsPlanned;

    @Column(name = "items_collected", nullable = false)
    private int itemsCollected;

    @Column(name = "items_processed", nullable = false)
    private int itemsProcessed;

    @Column(name = "items_published", nullable = false)
    private int itemsPublished;

    @Column(name = "items_duplicate", nullable = false)
    private int itemsDuplicate;

    @Column(name = "items_rejected", nullable = false)
    private int itemsRejected;

    @Column(name = "items_failed", nullable = false)
    private int itemsFailed;

    @Column(name = "notes", length = 2000)
    private String notes;

    public static PipelineRun start(RunTrigger trigger, int topicsPlanned) {
        PipelineRun run = new PipelineRun();
        run.id = UUID.randomUUID();
        run.trigger = trigger;
        run.status = RunStatus.RUNNING;
        run.startedAt = Instant.now();
        run.topicsPlanned = topicsPlanned;
        return run;
    }

    public void finish(RunStatus status, int itemsCollected, String notes) {
        this.status = status;
        this.finishedAt = Instant.now();
        this.itemsCollected = itemsCollected;
        this.notes = notes == null || notes.length() <= 2000 ? notes : notes.substring(0, 2000);
    }
}
