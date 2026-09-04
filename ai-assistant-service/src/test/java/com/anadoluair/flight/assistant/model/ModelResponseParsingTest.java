package com.anadoluair.flight.assistant.model;

import com.anadoluair.flight.assistant.agent.ModelReply;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model yanıtlarının ayrıştırılması.
 *
 * <p>Burada ağa çıkılmıyor. Gerçek servislerin döndüğü gövdeler metin olarak
 * yazılı ve doğrudan ayrıştırıcıya veriliyor. Sebebi: ayrıştırma, bu entegrasyonun
 * en kolay bozulan yeri. Sağlayıcı alan adını değiştirirse ya da model bazen araç
 * çağırıp bazen metin dönerse, hata burada çıkar. Ağ çağrısı kurmadan test
 * edebilmek için ayrıştırma saf bir metoda ayrıldı.
 */
class ModelResponseParsingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static Map<?, ?> parseJson(String body) throws Exception {
        return JSON.readValue(body, Map.class);
    }

    @Nested
    @DisplayName("Ollama")
    class Ollama {

        @Test
        @DisplayName("Arac cagrisi iceren yanit ToolCall'a cevrilir")
        void toolCall() throws Exception {
            String body = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "",
                    "tool_calls": [
                      {"function": {"name": "flight_status", "arguments": {"flightNumber": "ZZ123"}}}
                    ]
                  }
                }
                """;

            ModelReply reply = OllamaChatModel.parse(parseJson(body));

            assertThat(reply.wantsTool()).isTrue();
            assertThat(reply.toolCall().tool()).isEqualTo("flight_status");
            assertThat(reply.toolCall().argument("flightNumber")).isEqualTo("ZZ123");
        }

        @Test
        @DisplayName("Duz metin yaniti cevap olarak doner")
        void plainText() throws Exception {
            String body = """
                {"message": {"role": "assistant", "content": "ZZ123 saat 10:00'da kalkiyor."}}
                """;

            ModelReply reply = OllamaChatModel.parse(parseJson(body));

            assertThat(reply.wantsTool()).isFalse();
            assertThat(reply.text()).isEqualTo("ZZ123 saat 10:00'da kalkiyor.");
        }

        @Test
        @DisplayName("Bozuk yanitlarda cokmez")
        void malformed() throws Exception {
            assertThat(OllamaChatModel.parse(null).wantsTool()).isFalse();
            assertThat(OllamaChatModel.parse(parseJson("{}")).wantsTool()).isFalse();
            assertThat(OllamaChatModel.parse(parseJson("""
                {"message": {"role": "assistant", "tool_calls": []}}
                """)).wantsTool()).isFalse();
        }
    }

    @Nested
    @DisplayName("Gemini")
    class Gemini {

        @Test
        @DisplayName("functionCall iceren yanit ToolCall'a cevrilir")
        void functionCall() throws Exception {
            String body = """
                {
                  "candidates": [{
                    "content": {
                      "role": "model",
                      "parts": [
                        {"functionCall": {"name": "flight_bookable", "args": {"flightNumber": "ZZ7"}}}
                      ]
                    }
                  }]
                }
                """;

            ModelReply reply = GeminiChatModel.parse(parseJson(body));

            assertThat(reply.wantsTool()).isTrue();
            assertThat(reply.toolCall().tool()).isEqualTo("flight_bookable");
            assertThat(reply.toolCall().argument("flightNumber")).isEqualTo("ZZ7");
        }

        @Test
        @DisplayName("Metin parcasi olan yanit cevap olarak doner")
        void text() throws Exception {
            String body = """
                {"candidates": [{"content": {"role": "model", "parts": [{"text": "Rezervasyona acik."}]}}]}
                """;

            ModelReply reply = GeminiChatModel.parse(parseJson(body));

            assertThat(reply.wantsTool()).isFalse();
            assertThat(reply.text()).isEqualTo("Rezervasyona acik.");
        }

        @Test
        @DisplayName("Metin ve functionCall birlikte gelirse functionCall kazanir")
        void functionCallWinsOverText() throws Exception {
            // Gemini bazen once bir aciklama metni, sonra arac cagrisi doner.
            // Metni cevap sanip donersek arac hic calismaz ve kullanici yanlis cevap alir.
            String body = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [
                        {"text": "Kontrol ediyorum..."},
                        {"functionCall": {"name": "flight_status", "args": {"flightNumber": "ZZ1"}}}
                      ]
                    }
                  }]
                }
                """;

            ModelReply reply = GeminiChatModel.parse(parseJson(body));

            assertThat(reply.wantsTool()).isTrue();
            assertThat(reply.toolCall().tool()).isEqualTo("flight_status");
        }

        @Test
        @DisplayName("Bos ve bozuk yanitlarda cokmez")
        void malformed() throws Exception {
            assertThat(GeminiChatModel.parse(null).wantsTool()).isFalse();
            assertThat(GeminiChatModel.parse(parseJson("{}")).wantsTool()).isFalse();
            assertThat(GeminiChatModel.parse(parseJson("""
                {"candidates": []}
                """)).wantsTool()).isFalse();
        }
    }
}
