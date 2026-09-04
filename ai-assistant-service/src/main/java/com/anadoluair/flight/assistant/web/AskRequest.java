package com.anadoluair.flight.assistant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param question kullanıcının sorusu
 * @param model    kullanılacak model adı; boş bırakılırsa varsayılan kullanılır
 */
public record AskRequest(
        @NotBlank(message = "Soru bos olamaz")
        @Size(max = 500, message = "Soru en fazla 500 karakter olabilir")
        String question,

        String model) {
}
