package com.anadoluair.flight.assistant.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API testleri.
 *
 * <p>Varsayılan model sahte olduğu için bu testler ağa hiç çıkmaz ve API anahtarı
 * gerektirmez. CI'da da aynı şekilde çalışır. Uygulamanın varsayılan halinin
 * gerçekten çalıştığını da bu testler kanıtlıyor: depoyu indiren biri hiçbir
 * kurulum yapmadan aynı sonucu alır.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Model listesi hangi modelin anahtar istedigini soyler")
    void listsModels() throws Exception {
        mockMvc.perform(get("/api/assistant/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'stub')].requiresApiKey").value(false))
                .andExpect(jsonPath("$[?(@.name == 'stub')].isDefault").value(true))
                .andExpect(jsonPath("$[?(@.name == 'gemini')].requiresApiKey").value(true));
    }

    @Test
    @DisplayName("Model belirtilmezse varsayilan kullanilir ve istek anahtarsiz calisir")
    void worksWithoutAnyConfiguration() throws Exception {
        String body = objectMapper.writeValueAsString(
                new AskRequest("ZZ123 ucusu nerede?", null));

        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("stub"))
                .andExpect(jsonPath("$.answer").isNotEmpty());
    }

    @Test
    @DisplayName("Olmayan model istenirse 400 ve hangi modellerin oldugu doner")
    void unknownModelIsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(
                new AskRequest("merhaba", "gpt-5-ultra"));

        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("stub")));
    }

    @Test
    @DisplayName("Bos soru reddedilir")
    void blankQuestionIsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(new AskRequest("   ", null));

        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API anahtari yanitin HICBIR yerinde gorunmez")
    void apiKeyNeverAppearsInTheResponse() throws Exception {
        // Anahtar sizintisi sessiz bir hatadir: kimse fark etmez, log toplayan
        // sistemler anahtari saklar ve anahtar baskasinin eline gecer. Bu yuzden
        // yanitin tamamini metin olarak tarayip anahtari ariyoruz.
        String secret = "AIzaSy-BU-ANAHTAR-YANITA-SIZMAMALI";
        String body = objectMapper.writeValueAsString(new AskRequest("ZZ123 nerede?", null));

        MvcResult result = mockMvc.perform(post("/api/assistant/ask")
                        .header(AssistantController.API_KEY_HEADER, secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(secret);

        // Yanit basliklarina da sizmamali.
        result.getResponse().getHeaderNames().forEach(header ->
                assertThat(String.valueOf(result.getResponse().getHeader(header)))
                        .doesNotContain(secret));
    }

    @Test
    @DisplayName("Yanit, hangi araclarin cagrildigini gosterir")
    void showsWhichToolsWereCalled() throws Exception {
        // Cevabin nereden geldigi gorunmezse, yanlis cevabi ayikla yamaktan
        // baska yol kalmaz. 'steps' alani bu yuzden var.
        String body = objectMapper.writeValueAsString(
                new AskRequest("ABC123 rezervasyonum ne durumda?", null));

        MvcResult result = mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(response.get("steps")).isNotNull();
    }
}
