package com.anadoluair.flight.assistant.agent;

import com.anadoluair.flight.assistant.model.ChatModel;
import com.anadoluair.flight.assistant.tool.Tool;
import com.anadoluair.flight.assistant.tool.ToolRegistry;
import com.anadoluair.flight.assistant.tool.ToolSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent döngüsünün testleri.
 *
 * <p>Model burada sahte. Gerçek bir modelle test etmek yanlış olurdu: aynı soruya
 * her seferinde farklı cevap gelir, testin sonucu da her koşuda değişir. Sahte
 * modelle modelin ne diyeceğini biz yazarız ve döngünün buna nasıl tepki verdiğini
 * ölçeriz. Test edilen şey model değil, DÖNGÜ.
 */
class AgentTest {

    /** Sırayla önceden yazılmış cevapları veren model. */
    private static class ScriptedModel implements ChatModel {
        private final Deque<ModelReply> replies = new ArrayDeque<>();
        private final List<List<Message>> seenConversations = new ArrayList<>();

        ScriptedModel(ModelReply... scripted) {
            this.replies.addAll(List.of(scripted));
        }

        @Override public String name() { return "scripted"; }
        @Override public boolean requiresApiKey() { return false; }

        @Override
        public ModelReply reply(List<Message> conversation, List<ToolSpec> tools, String apiKey) {
            seenConversations.add(List.copyOf(conversation));
            // Senaryo bittiyse hep arac istemeye devam et: tur sinirini test etmek icin.
            return replies.isEmpty()
                    ? ModelReply.callTool(new ToolCall("echo", Map.of("value", "yine")))
                    : replies.poll();
        }
    }

    private static Tool echoTool() {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("echo", "Verileni geri doner", Map.of("value", "yankilanacak metin"));
            }
            @Override
            public String execute(Map<String, Object> arguments) {
                return "echo: " + arguments.get("value");
            }
        };
    }

    private static Tool explodingTool() {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("explode", "Her zaman patlar", Map.of());
            }
            @Override
            public String execute(Map<String, Object> arguments) {
                throw new IllegalStateException("ucus servisi kapali");
            }
        };
    }

    @Test
    @DisplayName("Model arac istemezse cevap dogrudan doner")
    void answersWithoutTools() {
        Agent agent = new Agent(new ToolRegistry(List.of(echoTool())), 5);

        AgentResult result = agent.ask("merhaba",
                new ScriptedModel(ModelReply.answer("selam")), null);

        assertThat(result.answer()).isEqualTo("selam");
        assertThat(result.steps()).isEmpty();
        assertThat(result.completed()).isTrue();
    }

    @Test
    @DisplayName("Model arac isterse arac calisir ve sonucu modele geri gider")
    void runsToolAndFeedsResultBack() {
        ScriptedModel model = new ScriptedModel(
                ModelReply.callTool(new ToolCall("echo", Map.of("value", "ZZ123"))),
                ModelReply.answer("ucus bulundu"));
        Agent agent = new Agent(new ToolRegistry(List.of(echoTool())), 5);

        AgentResult result = agent.ask("ZZ123 nerede?", model, null);

        assertThat(result.answer()).isEqualTo("ucus bulundu");
        assertThat(result.steps()).singleElement()
                .satisfies(step -> {
                    assertThat(step.tool()).isEqualTo("echo");
                    assertThat(step.result()).isEqualTo("echo: ZZ123");
                });

        // Ikinci cagrida model, aracin sonucunu konusmanin icinde GORMELI.
        // Gormezse arac cagirmanin bir anlami kalmaz.
        List<Message> secondCall = model.seenConversations.get(1);
        assertThat(secondCall)
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo(Message.Role.TOOL);
                    assertThat(message.text()).isEqualTo("echo: ZZ123");
                });
    }

    @Test
    @DisplayName("Model surekli arac isterse tur sinirinda durur")
    void stopsAtTurnLimit() {
        // Senaryo bos: model her turda yeniden arac isteyecek, hic cevap vermeyecek.
        Agent agent = new Agent(new ToolRegistry(List.of(echoTool())), 3);

        AgentResult result = agent.ask("sonsuza kadar", new ScriptedModel(), null);

        assertThat(result.completed()).isFalse();
        assertThat(result.steps()).hasSize(3);
        assertThat(result.answer()).contains("adim sayisi");
    }

    @Test
    @DisplayName("Olmayan bir arac istenirse uygulama cokmez, model uyarilir")
    void unknownToolDoesNotCrash() {
        ScriptedModel model = new ScriptedModel(
                ModelReply.callTool(new ToolCall("ucak_kaldir", Map.of())),
                ModelReply.answer("tamam, anladim"));
        Agent agent = new Agent(new ToolRegistry(List.of(echoTool())), 5);

        AgentResult result = agent.ask("ucagi kaldir", model, null);

        assertThat(result.completed()).isTrue();
        assertThat(result.steps()).singleElement()
                .satisfies(step -> assertThat(step.result())
                        .contains("ucak_kaldir")
                        .contains("arac yok"));
    }

    @Test
    @DisplayName("Arac hata firlatirsa akis kesilmez, hata modele anlatilir")
    void toolFailureIsReportedNotThrown() {
        ScriptedModel model = new ScriptedModel(
                ModelReply.callTool(new ToolCall("explode", Map.of())),
                ModelReply.answer("servise su an ulasilamiyor"));
        Agent agent = new Agent(new ToolRegistry(List.of(echoTool(), explodingTool())), 5);

        AgentResult result = agent.ask("patlat", model, null);

        assertThat(result.completed()).isTrue();
        assertThat(result.steps()).singleElement()
                .satisfies(step -> assertThat(step.result()).contains("ucus servisi kapali"));
    }
}
