package com.anadoluair.flight.assistant.model;

import com.anadoluair.flight.assistant.agent.Message;
import com.anadoluair.flight.assistant.agent.ModelReply;
import com.anadoluair.flight.assistant.agent.ToolCall;
import com.anadoluair.flight.assistant.tool.ToolSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini'ye bağlanır. Ücretsiz katman için Google AI Studio'dan alınan
 * bir API anahtarı yeterlidir; Google Cloud projesi kurmaya gerek yoktur.
 *
 * <p><b>Anahtar nasıl taşınıyor:</b> Gemini anahtarı sorgu parametresi olarak da
 * kabul eder ({@code ?key=...}). Burada bilerek <b>başlık</b> kullanılıyor
 * ({@code x-goog-api-key}). Sebebi şu: URL'ler her yere yazılır. Erişim logları,
 * hata izleri, ara sunucu kayıtları, tarayıcı geçmişi. Anahtar URL'de olsaydı
 * bunların hepsine sızardı. Başlıklar bu kayıtlara girmez.
 *
 * <p>Anahtar hiçbir alanda tutulmaz, diske yazılmaz, loglanmaz. Yalnızca bu
 * çağrının süresi boyunca bellekte kalır.
 */
@Component
public class GeminiChatModel implements ChatModel {

    private final RestClient client;
    private final String model;

    public GeminiChatModel(RestClient.Builder builder,
                           @Value("${assistant.gemini.base-url}") String baseUrl,
                           @Value("${assistant.gemini.model}") String model) {
        this.client = builder.clone().baseUrl(baseUrl).build();
        this.model = model;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public boolean requiresApiKey() {
        return true;
    }

    @Override
    public ModelReply reply(List<Message> conversation, List<ToolSpec> tools, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return ModelReply.answer("Gemini icin API anahtari gerekiyor. Anahtari istekle birlikte gonder.");
        }

        Map<String, Object> body = buildRequestBody(conversation, tools);

        Map<?, ?> response;
        try {
            response = client.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            // Gemini hatayi govdede aciklar: "API key not valid", "quota exceeded"...
            // Bunu yutmak, kullanicinin sorunu kendi kodunda aramasina yol acar.
            // Hangi modelin denendigi mesaja giriyor: "model artik yok" turu
            // hatalarda hangi adin gecersiz oldugunu bilmeden duzeltilemez.
            throw new ModelCallException(name(), e.getStatusCode().value(),
                    "[%s] %s".formatted(model, extractError(e.getResponseBodyAsString())), e);
        } catch (ResourceAccessException e) {
            throw new ModelCallException(name(), 0, "Gemini'ye ulasilamadi: " + e.getMessage(), e);
        }

        return parse(response);
    }

    /**
     * Gemini'nin hata gövdesinden okunabilir mesajı çıkarır.
     *
     * <p>Gövde {@code {"error": {"message": "..."}}} şeklindedir. Ham JSON'u
     * kullanıcıya vermek yerine yalnızca mesajı alıyoruz.
     */
    private static String extractError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Saglayici bir aciklama dondurmedi.";
        }
        try {
            Map<?, ?> parsed = new ObjectMapper().readValue(responseBody, Map.class);
            Object error = parsed.get("error");
            if (error instanceof Map<?, ?> errorMap && errorMap.get("message") != null) {
                return String.valueOf(errorMap.get("message"));
            }
        } catch (Exception ignored) {
            // Govde JSON degilse ham metne dusuyoruz.
        }
        return responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
    }

    /**
     * Gemini yanıtını {@link ModelReply}'a çevirir.
     *
     * <p>Ağ çağrısı içermeyen saf metot: kaydedilmiş yanıt gövdeleriyle test edilir.
     */
    @SuppressWarnings("unchecked")
    static ModelReply parse(Map<?, ?> response) {
        if (response == null) {
            return ModelReply.answer("Gemini bos yanit dondu.");
        }

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return ModelReply.answer("Gemini bir cevap uretmedi.");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            return ModelReply.answer("Gemini yanitinda icerik yok.");
        }

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            return ModelReply.answer("Gemini yanitinda icerik yok.");
        }

        for (Map<String, Object> part : parts) {
            Map<String, Object> functionCall = (Map<String, Object>) part.get("functionCall");
            if (functionCall != null) {
                String name = String.valueOf(functionCall.get("name"));
                Map<String, Object> arguments = (Map<String, Object>) functionCall.get("args");
                return ModelReply.callTool(new ToolCall(name, arguments == null ? Map.of() : arguments));
            }
        }

        return ModelReply.answer(String.valueOf(parts.get(0).getOrDefault("text", "")));
    }

    /**
     * İstek gövdesini kurar.
     *
     * <p>Ağ çağrısı içermeyen saf metot: gövdenin doğru kurulduğu testle sabitlenir.
     */
    static Map<String, Object> buildRequestBody(List<Message> conversation, List<ToolSpec> tools) {
        Map<String, Object> body = new LinkedHashMap<>();

        // Sistem talimati AYRI bir alandir, siradan bir mesaj degil. Konusmanin
        // icine sikistirilirsa model onu kullanicinin soyledigi bir sey sanar ve
        // sonraki turlarda agirligini kaybeder.
        String instruction = conversation.stream()
                .filter(message -> message.role() == Message.Role.SYSTEM)
                .map(Message::text)
                .findFirst()
                .orElse(null);
        if (instruction != null) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", instruction))));
        }

        body.put("contents", toGeminiContents(conversation));
        body.put("tools", List.of(Map.of("functionDeclarations", toFunctionDeclarations(tools))));
        return body;
    }

    private static List<Map<String, Object>> toGeminiContents(List<Message> conversation) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Message message : conversation) {
            // Sistem talimati yukarida ayri alana kondu, burada tekrarlanmaz.
            if (message.role() == Message.Role.SYSTEM) {
                continue;
            }
            // Gemini yalnizca "user" ve "model" rollerini bilir. Arac sonucunu
            // kullanicidan gelen bir bilgi gibi aktariyoruz; boylece model onu
            // cevabini kurarken kullanabiliyor.
            String role = message.role() == Message.Role.ASSISTANT ? "model" : "user";
            String text = message.role() == Message.Role.TOOL
                    ? "%s araci sunu dondu: %s".formatted(message.tool(), message.text())
                    : message.text();
            contents.add(Map.of("role", role, "parts", List.of(Map.of("text", text))));
        }
        return contents;
    }

    private static List<Map<String, Object>> toFunctionDeclarations(List<ToolSpec> tools) {
        List<Map<String, Object>> declarations = new ArrayList<>();
        for (ToolSpec tool : tools) {
            Map<String, Object> properties = new LinkedHashMap<>();
            tool.parameters().forEach((parameter, description) ->
                    properties.put(parameter, Map.of("type", "string", "description", description)));

            declarations.add(Map.of(
                    "name", tool.name(),
                    "description", tool.description(),
                    "parameters", Map.of(
                            "type", "object",
                            "properties", properties,
                            "required", List.copyOf(tool.parameters().keySet()))));
        }
        return declarations;
    }
}
