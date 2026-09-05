package com.anadoluair.flight.assistant.web;

import com.anadoluair.flight.assistant.agent.Message;
import com.anadoluair.flight.assistant.agent.ModelReply;
import com.anadoluair.flight.assistant.model.ChatModel;
import com.anadoluair.flight.assistant.model.ModelCallException;
import com.anadoluair.flight.assistant.tool.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sağlayıcı çağrısı başarısız olduğunda ne dönüyoruz?
 *
 * <p>Bu testler gerçek bir hatadan sonra yazıldı. Kullanıcı Gemini anahtarını girdi
 * ve <b>"Internal Server Error"</b> aldı. Sunucuda bir arıza yoktu; Gemini "API key
 * not valid" diyordu ama bu mesaj yutuluyordu. Kullanıcı sorunu kendi tarafında
 * aradı — çünkü 500, "bende bir şey bozuk" demektir.
 *
 * <p>Doğru davranış, sağlayıcının söylediğini kullanıcıya iletmek ve durum kodunu
 * kimin düzeltebileceğine göre seçmektir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = ModelFailureTest.FailingModels.class)
class ModelFailureTest {

    /** Her çağrıda sağlayıcı hatası fırlatan sahte modeller. */
    @TestConfiguration
    static class FailingModels {

        @Bean
        ChatModel rejectingModel() {
            return failing("reddeden", 400, "API key not valid. Please pass a valid API key.");
        }

        @Bean
        ChatModel unreachableModel() {
            return failing("ulasilamayan", 0, "Ollama'ya ulasilamadi. Calisiyor mu?");
        }

        private static ChatModel failing(String name, int status, String reason) {
            return new ChatModel() {
                @Override public String name() { return name; }
                @Override public boolean requiresApiKey() { return false; }
                @Override
                public ModelReply reply(List<Message> conversation, List<ToolSpec> tools, String apiKey) {
                    throw new ModelCallException(name, status, reason, null);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MvcResult ask(String model) throws Exception {
        return mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskRequest("ZZ1 nerede?", model))))
                .andReturn();
    }

    @Test
    @DisplayName("Geçersiz anahtar 500 DEĞİL 400 döner ve sebebini söyler")
    void invalidKeyIsTheCallersProblem() throws Exception {
        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskRequest("ZZ1 nerede?", "reddeden"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("API key not valid")))
                .andExpect(jsonPath("$.provider").value("reddeden"))
                .andExpect(jsonPath("$.upstreamStatus").value(400));
    }

    @Test
    @DisplayName("Sağlayıcıya ulaşılamazsa 502 döner: bu çağıranın hatası değil")
    void unreachableProviderIsNotTheCallersProblem() throws Exception {
        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskRequest("ZZ1 nerede?", "ulasilamayan"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("ulasilamadi")));
    }

    @Test
    @DisplayName("Hata yanıtı API anahtarını sızdırmaz")
    void errorResponseDoesNotLeakTheKey() throws Exception {
        String secret = "AIzaSy-BU-ANAHTAR-HATA-YANITINA-SIZMAMALI";

        MvcResult result = mockMvc.perform(post("/api/assistant/ask")
                        .header(AssistantController.API_KEY_HEADER, secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskRequest("ZZ1 nerede?", "reddeden"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Hata yollari anahtar sizintisi icin en olasi yerdir: istisna mesajlari
        // genelde "su istek su parametrelerle basarisiz oldu" diye yazilir.
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
    }
}
