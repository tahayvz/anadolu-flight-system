package com.anadoluair.flight.assistant.agent;

import java.util.Map;

/**
 * Agent'ın attığı tek bir adım: hangi aracı, hangi argümanla çağırdı, ne aldı.
 *
 * <p>Bunu kaydediyoruz çünkü modelin neden o cevabı verdiği aksi halde
 * görünmez oluyor. Cevap yanlışsa, hangi adımda saptığını buradan okursun.
 */
public record AgentStep(String tool, Map<String, Object> arguments, String result) {
}
