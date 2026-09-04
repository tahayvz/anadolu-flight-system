package com.anadoluair.flight.assistant.model;

import java.util.Collection;

public class UnknownModelException extends RuntimeException {

    public UnknownModelException(String requested, Collection<String> available) {
        super("'%s' diye bir model yok. Kullanilabilir modeller: %s"
                .formatted(requested, String.join(", ", available)));
    }
}
