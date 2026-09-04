package com.anadoluair.flight.assistant.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Kayıtlı modelleri tutar ve isme göre verir. */
@Slf4j
@Component
public class ChatModels {

    private final Map<String, ChatModel> byName;
    private final String defaultModel;

    public ChatModels(List<ChatModel> models,
                      @Value("${assistant.default-model}") String defaultModel) {
        this.byName = models.stream().collect(Collectors.toMap(ChatModel::name, Function.identity()));
        this.defaultModel = defaultModel;

        if (!byName.containsKey(defaultModel)) {
            throw new IllegalStateException(
                    "assistant.default-model '%s' diye bir model yok. Olanlar: %s"
                            .formatted(defaultModel, byName.keySet()));
        }
        log.info("Kayitli modeller: {}, varsayilan: {}", byName.keySet(), defaultModel);
    }

    public List<ChatModel> all() {
        return List.copyOf(byName.values());
    }

    /** İsim boşsa varsayılanı döner. Bilinmeyen isimde açık hata verir. */
    public ChatModel byName(String name) {
        String wanted = (name == null || name.isBlank()) ? defaultModel : name;
        ChatModel model = byName.get(wanted);
        if (model == null) {
            throw new UnknownModelException(wanted, byName.keySet());
        }
        return model;
    }

    public String defaultModel() {
        return defaultModel;
    }
}
