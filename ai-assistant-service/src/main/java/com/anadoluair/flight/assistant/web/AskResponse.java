package com.anadoluair.flight.assistant.web;

import com.anadoluair.flight.assistant.agent.AgentResult;
import com.anadoluair.flight.assistant.agent.AgentStep;

import java.util.List;

/**
 * @param answer    kullanıcıya gösterilecek cevap
 * @param model     cevabı üreten model
 * @param completed model cevaba ulaştı mı, yoksa tur sınırına mı takıldı
 * @param steps     çağrılan araçlar; cevabın nereden geldiğini görmek için
 */
public record AskResponse(String answer, String model, boolean completed, List<AgentStep> steps) {

    public static AskResponse from(AgentResult result, String model) {
        return new AskResponse(result.answer(), model, result.completed(), result.steps());
    }
}
