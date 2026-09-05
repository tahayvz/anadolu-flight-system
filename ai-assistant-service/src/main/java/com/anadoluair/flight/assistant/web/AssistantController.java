package com.anadoluair.flight.assistant.web;

import com.anadoluair.flight.assistant.agent.Agent;
import com.anadoluair.flight.assistant.agent.AgentResult;
import com.anadoluair.flight.assistant.agent.Message;
import com.anadoluair.flight.assistant.model.ChatModel;
import com.anadoluair.flight.assistant.model.ChatModels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "Assistant", description = "Ucus servisleri uzerinde arac cagiran asistan")
public class AssistantController {

    /**
     * API anahtarının taşındığı başlık.
     *
     * <p>Anahtar sorgu parametresiyle değil başlıkla alınır. URL'ler erişim
     * loglarına, hata izlerine ve ara sunucu kayıtlarına yazılır; başlıklar yazılmaz.
     */
    public static final String API_KEY_HEADER = "X-Model-Api-Key";

    private final Agent agent;
    private final ChatModels models;

    public AssistantController(Agent agent, ChatModels models) {
        this.agent = agent;
        this.models = models;
    }

    @Operation(summary = "Kullanilabilir modelleri listeler",
            description = "Arayuz bu listeye bakarak model secimini kurar ve anahtar isteyip istemeyecegine karar verir.")
    @GetMapping("/models")
    public ResponseEntity<List<ModelInfo>> models() {
        List<ModelInfo> result = models.all().stream()
                .map(model -> new ModelInfo(
                        model.name(),
                        model.requiresApiKey(),
                        model.name().equals(models.defaultModel())))
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Asistana soru sorar",
            description = "Model gerekli gordugunde ucus servislerini cagirir. Hangi araclarin "
                    + "cagrildigi yanittaki 'steps' alaninda doner.")
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(
            @Valid @RequestBody AskRequest request,
            @Parameter(description = "Yalnizca anahtar isteyen modeller icin gerekli. Saklanmaz.")
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {

        ChatModel model = models.byName(request.model());

        // Sorunun kendisi loglanmiyor: kullanici PNR gibi kisisel veri yazabilir.
        log.info("Asistan istegi alindi, model={}", model.name());

        List<Message> history = request.historyOrEmpty().stream()
                .map(turn -> turn.fromUser()
                        ? Message.user(turn.text())
                        : Message.assistant(turn.text()))
                .toList();

        AgentResult result = agent.ask(request.question(), history, model, apiKey);
        return ResponseEntity.ok(AskResponse.from(result, model.name()));
    }
}
