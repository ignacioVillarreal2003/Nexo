package com.nexo.content.infrastructure.repository;

import com.nexo.content.domain.entity.enums.RunCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Incrementos atómicos de los contadores de una ejecución: varios consumidores procesan documentos
 * de la misma ejecución en paralelo, así que no se puede leer, sumar y escribir la entidad.
 */
@Repository
@RequiredArgsConstructor
public class PipelineCounters {

    private final JdbcTemplate jdbcTemplate;

    public void increment(UUID runId, RunCounter counter) {
        if (runId == null) {
            return;
        }
        // La columna sale de un enum cerrado, nunca de la entrada.
        jdbcTemplate.update("update pipeline_runs set " + counter.column() + " = " + counter.column()
                + " + 1 where id = ?", runId);
    }
}
