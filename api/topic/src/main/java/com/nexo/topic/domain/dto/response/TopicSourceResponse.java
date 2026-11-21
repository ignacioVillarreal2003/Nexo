package com.nexo.topic.domain.dto.response;

import com.nexo.topic.domain.entity.enums.SourceType;

/** Fuente activa de un tópico, tal como se muestra al lector (sin URL ni configuración interna). */
public record TopicSourceResponse(String name, SourceType type) {
}
