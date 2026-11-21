package com.nexo.content.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.content.domain.config.ApplicationProperties;
import com.nexo.content.domain.dto.response.PageResponse;
import com.nexo.content.domain.entity.PipelineError;
import com.nexo.content.domain.entity.PipelineRun;
import com.nexo.content.domain.entity.enums.PipelineStage;
import com.nexo.content.domain.entity.enums.RunCounter;
import com.nexo.content.domain.entity.enums.RunStatus;
import com.nexo.content.domain.entity.enums.RunTrigger;
import com.nexo.content.domain.exception.BadRequestException;
import com.nexo.content.domain.exception.ResourceNotFoundException;
import com.nexo.content.infrastructure.repository.PipelineCounters;
import com.nexo.content.infrastructure.repository.PipelineErrorRepository;
import com.nexo.content.infrastructure.repository.PipelineRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Ejecuciones del pipeline: las abre y cierra el collector; los contadores crecen con el procesamiento. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineService {

    private final PipelineRunRepository runRepository;
    private final PipelineErrorRepository errorRepository;
    private final PipelineCounters counters;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public PipelineRun start(RunTrigger trigger, int topicsPlanned) {
        return runRepository.save(PipelineRun.start(trigger, topicsPlanned));
    }

    @Transactional
    public PipelineRun finish(UUID runId, RunStatus status, int itemsCollected, String notes) {
        if (!status.isFinal()) {
            throw new BadRequestException("A run can only be finished as COMPLETED, PARTIAL or FAILED");
        }
        PipelineRun run = runRepository.findById(runId).orElseThrow(() -> new ResourceNotFoundException("Run", runId));
        run.finish(status, itemsCollected, notes);
        return run;
    }

    /** Errores de una etapa; un runId desconocido no impide registrarlo. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordError(UUID runId, PipelineStage stage, String source, UUID topicId, String message) {
        UUID knownRun = runId != null && runRepository.existsById(runId) ? runId : null;
        errorRepository.save(PipelineError.of(knownRun, stage, source, topicId, message));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(UUID runId, RunCounter counter) {
        counters.increment(runId, counter);
    }

    /** Un documento agotó sus reintentos: se registra el error y se cuenta como fallido. */
    public void recordDeadLetter(String payload, Exception cause) {
        UUID runId = null;
        UUID topicId = null;
        String source = null;
        try {
            JsonNode body = objectMapper.readTree(payload);
            runId = uuid(body.path("runId").asText(null));
            topicId = uuid(body.path("topicId").asText(null));
            source = body.path("url").asText(body.path("sourceName").asText(null));
        } catch (Exception unparseable) {
            log.warn("Dead-lettered document is not valid JSON");
        }
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        try {
            recordError(runId, PipelineStage.AI, source, topicId, root.getClass().getSimpleName() + ": " + root.getMessage());
            increment(runId, RunCounter.FAILED);
        } catch (RuntimeException e) {
            log.error("Could not record the dead-lettered document", e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PipelineRun> runs(int page, int size) {
        return PageResponse.from(runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public PageResponse<PipelineError> errors(UUID runId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return PageResponse.from(runId == null ? errorRepository.findAllByOrderByCreatedAtDesc(pageable)
                : errorRepository.findByRunIdOrderByCreatedAtDesc(runId, pageable));
    }

    /** Si el collector se reinicia a mitad de una ejecución, nadie la cierra: se marca como fallida. */
    @Scheduled(fixedDelayString = "PT15M")
    @SchedulerLock(name = "pipeline-stale-runs", lockAtMostFor = "PT5M")
    @Transactional
    public void failStaleRuns() {
        Instant now = Instant.now();
        int failed = runRepository.failStale(RunStatus.RUNNING, RunStatus.FAILED,
                now.minus(properties.runs().staleAfter()), now);
        if (failed > 0) {
            log.warn("Marked {} stale collection runs as FAILED", failed);
        }
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
