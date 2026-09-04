package com.anadoluair.flight.assistant.model;

import com.anadoluair.flight.assistant.agent.Message;
import com.anadoluair.flight.assistant.agent.ModelReply;
import com.anadoluair.flight.assistant.tool.ToolSpec;

import java.util.List;

/**
 * Bir sohbet modeli.
 *
 * <p>Agent bu arayüze bağlıdır, somut bir sağlayıcıya değil. Bunun iki sonucu var:
 *
 * <ul>
 *   <li>Testler ağa çıkmaz. Sahte bir model koyar, sonucu kesin biliriz. Gerçek
 *       modelle test etmek yanlış olurdu: aynı soruya her seferinde farklı cevap
 *       gelir, test de her koşuda farklı sonuç verir.</li>
 *   <li>Sağlayıcı değiştirmek ayar meselesidir, kod meselesi değil.</li>
 * </ul>
 */
public interface ChatModel {

    /** Ayarlarda ve API'de kullanılan ad: "stub", "ollama", "gemini". */
    String name();

    /** Bu model çalışmak için kullanıcıdan API anahtarı ister mi? */
    boolean requiresApiKey();

    /**
     * Konuşmanın devamını üretir.
     *
     * @param conversation şimdiye kadarki konuşma, sırayla
     * @param tools        modelin çağırabileceği araçlar
     * @param apiKey       yalnızca {@link #requiresApiKey()} true ise anlamlı;
     *                     hiçbir yerde saklanmaz, sadece bu çağrıda kullanılır
     */
    ModelReply reply(List<Message> conversation, List<ToolSpec> tools, String apiKey);
}
