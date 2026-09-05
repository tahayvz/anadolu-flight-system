package com.anadoluair.flight.assistant.model;

import com.anadoluair.flight.assistant.agent.Message;
import com.anadoluair.flight.assistant.agent.ModelReply;
import com.anadoluair.flight.assistant.agent.ToolCall;
import com.anadoluair.flight.assistant.tool.ToolSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makinede çalışan Ollama sunucusuna bağlanır. Ücretsizdir, internet istemez.
 *
 * <p><b>Bilinen sınır:</b> araç çağırma, küçük modellerde güvenilir değildir. Model
 * aracı çağırması gerekirken cevabı uydurabilir. Bu, bu kodun hatası değil, küçük
 * modellerin bilinen davranışıdır. Ciddi bir sonuç isteniyorsa Gemini kullanılmalı.
 */
@Component
public class OllamaChatModel implements ChatModel {

    private final RestClient client;
    private final String model;

    public OllamaChatModel(RestClient.Builder builder,
                           @Value("${assistant.ollama.base-url}") String baseUrl,
                           @Value("${assistant.ollama.model}") String model) {
        this.client = builder.clone().baseUrl(baseUrl).build();
        this.model = model;
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    @Override
    public ModelReply reply(List<Message> conversation, List<ToolSpec> tools, String apiKey) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", toOllamaMessages(conversation),
                "tools", toOllamaTools(tools),
                "stream", false);

        Map<?, ?> response;
        try {
            response = client.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw new ModelCallException(name(), e.getStatusCode().value(),
                    e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            // En sik sebep: Ollama makinede hic calismiyor.
            throw new ModelCallException(name(), 0,
                    "Ollama'ya ulasilamadi. Calisiyor mu? (" + e.getMessage() + ")", e);
        }

        return parse(response);
    }

    /**
     * Ollama yanıtını {@link ModelReply}'a çevirir.
     *
     * <p>Ayrı ve saf bir metot: ağ çağrısı içermez, bu yüzden kaydedilmiş bir yanıt
     * gövdesiyle doğrudan test edilebilir.
     */
    @SuppressWarnings("unchecked")
    static ModelReply parse(Map<?, ?> response) {
        if (response == null) {
            return ModelReply.answer("Ollama bos yanit dondu.");
        }

        Map<String, Object> message = (Map<String, Object>) response.get("message");
        if (message == null) {
            return ModelReply.answer("Ollama yanitinda mesaj yok.");
        }

        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");
            String name = String.valueOf(function.get("name"));
            Map<String, Object> arguments = (Map<String, Object>) function.get("arguments");
            return ModelReply.callTool(new ToolCall(name, arguments == null ? Map.of() : arguments));
        }

        return ModelReply.answer(String.valueOf(message.getOrDefault("content", "")));
    }

    private List<Map<String, Object>> toOllamaMessages(List<Message> conversation) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : conversation) {
            String role = switch (message.role()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case TOOL -> "tool";
            };
            messages.add(Map.of("role", role, "content", message.text()));
        }
        return messages;
    }

    private List<Map<String, Object>> toOllamaTools(List<ToolSpec> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolSpec tool : tools) {
            Map<String, Object> properties = new LinkedHashMap<>();
            tool.parameters().forEach((parameter, description) ->
                    properties.put(parameter, Map.of("type", "string", "description", description)));

            result.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", properties,
                                    "required", List.copyOf(tool.parameters().keySet())))));
        }
        return result;
    }
}
