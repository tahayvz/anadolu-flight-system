package com.anadoluair.flight.assistant.web;

/**
 * Arayüzün model seçim listesini kurabilmesi için gereken bilgi.
 *
 * @param name           model adı
 * @param requiresApiKey seçilince kullanıcıdan anahtar istenmeli mi
 * @param isDefault      bu model varsayılan mı
 */
public record ModelInfo(String name, boolean requiresApiKey, boolean isDefault) {
}
