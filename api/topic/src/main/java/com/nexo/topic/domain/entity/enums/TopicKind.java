package com.nexo.topic.domain.entity.enums;

/** Qué se sigue al seguir un tópico. */
public enum TopicKind {
    /** Un tema: se buscan notas sobre él y la IA descarta las que no lo tratan. */
    TOPIC,
    /** Las noticias principales de un país. */
    COUNTRY,
    /** Todo lo que publica un diario o sitio en particular. */
    OUTLET
}
