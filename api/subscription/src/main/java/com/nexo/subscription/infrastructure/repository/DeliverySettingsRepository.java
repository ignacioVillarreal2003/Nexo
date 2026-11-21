package com.nexo.subscription.infrastructure.repository;

import com.nexo.subscription.domain.entity.DeliverySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeliverySettingsRepository extends JpaRepository<DeliverySettings, UUID> {

    /**
     * Usuarios con suscripciones activas cuya hora local de entrega coincide con la hora de {@code at}.
     * Quien todavía no configuró su entrega usa la zona y hora por defecto.
     */
    @Query(value = """
            select distinct s.user_id
            from subscriptions s
            left join delivery_settings d on d.user_id = s.user_id
            where s.status = 'ACTIVE'
              and coalesce(d.paused, false) = false
              and extract(hour from (cast(:at as timestamptz) at time zone coalesce(d.timezone, :defaultTimezone)))
                  = coalesce(d.delivery_hour, :defaultHour)
            """, nativeQuery = true)
    List<UUID> findUsersDueAt(Instant at, String defaultTimezone, int defaultHour);
}
