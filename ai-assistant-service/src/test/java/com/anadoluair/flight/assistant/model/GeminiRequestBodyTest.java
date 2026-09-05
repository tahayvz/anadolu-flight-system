package com.anadoluair.flight.assistant.model;

import com.anadoluair.flight.assistant.agent.AssistantInstruction;
import com.anadoluair.flight.assistant.agent.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gemini'ye gönderilen gövdenin doğru kurulduğu.
 *
 * <p>Ağ çağrısı yok: gövde kurma saf bir metoda ayrıldı, çünkü buradaki bir hata
 * ancak canlı denemede fark edilir ve o sırada da sebebi görünmez.
 */
class GeminiRequestBodyTest {

    @Test
    @DisplayName("Sistem talimati AYRI alana konur, mesaj olarak degil")
    void instructionGoesInItsOwnField() {
        List<Message> conversation = List.of(
                Message.system(AssistantInstruction.TEXT),
                Message.user("ZZ1 nerede?"));

        Map<String, Object> body = GeminiChatModel.buildRequestBody(conversation, List.of());

        // Ayri alanda olmali. Konusmanin icine sikistirilirsa model onu
        // kullanicinin soyledigi bir sey sanar ve tur sayisi arttikca agirligini
        // kaybeder -- yani kurallar zamanla unutulur.
        assertThat(body).containsKey("systemInstruction");
        assertThat(String.valueOf(body.get("systemInstruction"))).contains("ZZ");

        // Ve mesajlar arasinda TEKRARLANMAMALI.
        assertThat(String.valueOf(body.get("contents")))
                .contains("ZZ1 nerede?")
                .doesNotContain("KURGUSAL");
    }

    @Test
    @DisplayName("Arac sonucu konusmaya hangi araca ait oldugu belirtilerek girer")
    void toolResultsAreLabelled() {
        List<Message> conversation = List.of(
                Message.user("ZZ1 acik mi?"),
                Message.toolResult("flight_bookable", "Ucus ZZ1 rezervasyona ACIK."));

        Map<String, Object> body = GeminiChatModel.buildRequestBody(conversation, List.of());

        assertThat(String.valueOf(body.get("contents")))
                .contains("flight_bookable")
                .contains("rezervasyona ACIK");
    }
}
