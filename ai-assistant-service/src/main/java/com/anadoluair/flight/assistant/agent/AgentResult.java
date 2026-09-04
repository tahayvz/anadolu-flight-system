package com.anadoluair.flight.assistant.agent;

import java.util.List;

/**
 * Agent'ın işi bittiğinde dönen sonuç.
 *
 * @param answer    kullanıcıya gösterilecek cevap
 * @param steps     yol boyunca çağrılan araçlar, sırasıyla
 * @param completed model bir cevaba ulaştı mı, yoksa tur sınırına mı takıldı
 */
public record AgentResult(String answer, List<AgentStep> steps, boolean completed) {

    public static AgentResult answered(String answer, List<AgentStep> steps) {
        return new AgentResult(answer, List.copyOf(steps), true);
    }

    /**
     * Model tur sınırına kadar cevaba ulaşamadı. Sessizce boş dönmek yerine
     * bunu açıkça söylüyoruz: kullanıcı da, log okuyan da durumu bilmeli.
     */
    public static AgentResult ranOutOfTurns(List<AgentStep> steps) {
        return new AgentResult(
                "Soruyu verilen adim sayisi icinde cevaplayamadim. Daha net sorabilir misin?",
                List.copyOf(steps),
                false);
    }
}
