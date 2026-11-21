package com.nexo.delivery.domain.entity;

import com.nexo.delivery.domain.entity.enums.RunStatus;
import com.nexo.delivery.domain.entity.enums.RunTrigger;
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
@Table(name = "delivery_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryRun {

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

    @Column(name = "users_processed", nullable = false)
    private int usersProcessed;

    @Column(name = "deliveries_created", nullable = false)
    private int deliveriesCreated;

    @Column(name = "errors", nullable = false)
    private int errors;

    @Column(name = "notes", length = 2000)
    private String notes;

    public static DeliveryRun start(RunTrigger trigger) {
        DeliveryRun run = new DeliveryRun();
        run.id = UUID.randomUUID();
        run.trigger = trigger;
        run.status = RunStatus.RUNNING;
        run.startedAt = Instant.now();
        return run;
    }

    public void finish(int usersProcessed, int deliveriesCreated, int errors, String notes) {
        this.usersProcessed = usersProcessed;
        this.deliveriesCreated = deliveriesCreated;
        this.errors = errors;
        this.status = errors == 0 ? RunStatus.COMPLETED : usersProcessed > errors ? RunStatus.PARTIAL : RunStatus.FAILED;
        this.finishedAt = Instant.now();
        this.notes = notes == null || notes.length() <= 2000 ? notes : notes.substring(0, 2000);
    }
}
