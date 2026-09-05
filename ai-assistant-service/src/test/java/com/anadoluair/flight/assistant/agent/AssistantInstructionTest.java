package com.anadoluair.flight.assistant.agent;

import com.anadoluair.flight.assistant.model.ChatModel;
import com.anadoluair.flight.assistant.tool.ToolRegistry;
import com.anadoluair.flight.assistant.tool.ToolSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modele verdiğimiz kurallar gerçekten gidiyor mu?
 *
 * <p>Bu testler bir hatadan sonra yazıldı. Uçuş sorulduğunda model <b>"TK1234"</b>
 * gibi numaralar üretiyordu. TK gerçek bir taşıyıcının kodudur. Depodaki tüm marka
 * izleri temizlenmişti — iz modelin kendi eğitim verisinden geliyordu.
 *
 * <p>Sebep: modele hiçbir talimat verilmiyordu. Bir modele kim olduğunu ve neyi
 * uydurmaması gerektiğini söylemezsen, boşluğu kendi varsayımıyla doldurur. Marka
 * temizliği bu yüzden yalnızca kaynak kodda yapılamaz.
 */
class AssistantInstructionTest {

    /** Kendisine verilen konuşmayı kaydeden model. */
    private static class RecordingModel implements ChatModel {
        List<Message> seen = new ArrayList<>();

        @Override public String name() { return "recording"; }
        @Override public boolean requiresApiKey() { return false; }

        @Override
        public ModelReply reply(List<Message> conversation, List<ToolSpec> tools, String apiKey) {
            seen = List.copyOf(conversation);
            return ModelReply.answer("tamam");
        }
    }

    @Test
    @DisplayName("Kurallar konusmanin EN BASINDA modele gider")
    void instructionLeadsTheConversation() {
        RecordingModel model = new RecordingModel();
        new Agent(new ToolRegistry(List.of()), 5).ask("ucuslar nasil?", model, null);

        assertThat(model.seen).isNotEmpty();
        assertThat(model.seen.get(0).role()).isEqualTo(Message.Role.SYSTEM);
        assertThat(model.seen.get(0).text()).isEqualTo(AssistantInstruction.TEXT);
    }

    @Test
    @DisplayName("Kurallar gercek tasiyicilari ve ZZ onekini ACIKCA soyluyor")
    void instructionNamesTheConstraints() {
        // Bu iddialar metnin ICERIGINI sabitliyor. Biri silinirse model yine
        // uydurmaya baslar ve bunu ancak canli deneyerek fark ederiz.
        assertThat(AssistantInstruction.TEXT)
                .contains("ZZ")
                .contains("KURGUSAL")
                .containsIgnoringCase("uydurma")
                .containsIgnoringCase("Turk Hava Yollari");
    }

}
