package com.anadoluair.flight.assistant.model;

import com.anadoluair.flight.assistant.agent.Message;
import com.anadoluair.flight.assistant.agent.ModelReply;
import com.anadoluair.flight.assistant.tool.Tool;
import com.anadoluair.flight.assistant.tool.ToolSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Aynı işin Spring AI ile yapılmış hali.
 *
 * <p>Bu sınıf bir karşılaştırma için var. Ana dalda araç çağırma döngüsü
 * {@code Agent} içinde elle yazılı; burada döngüyü framework yürütüyor.
 *
 * <p><b>Aradaki fark ve bedeli:</b> {@code ChatClient} araçları kendi çağırır ve
 * yalnızca nihai metni döner. Kod kısalır — ama üç şeyi kaybederiz:
 *
 * <ol>
 *   <li><b>Adımlar görünmez.</b> Hangi aracın çağrıldığı dışarı çıkmaz, bu yüzden
 *       bu modelde yanıttaki {@code steps} listesi boş kalır. Cevap yanlışsa
 *       nereden saptığını okuyamayız.</li>
 *   <li><b>Tur sınırı bizim elimizde değil.</b> Döngüyü framework yönetir.</li>
 *   <li><b>Hata davranışını biz seçemeyiz.</b> Elle yazılan döngüde olmayan bir
 *       araç adı ya da patlayan bir servis, modele cümleyle anlatılıp akış devam
 *       ediyordu. Burada davranış framework'e ait.</li>
 * </ol>
 *
 * <p>Karşılığında kazandığımız şey: yaklaşık 150 satır daha az kod ve sağlayıcı
 * değiştirmenin tek bağımlılık meselesi olması.
 */
@Slf4j
@Component
public class SpringAiChatModel implements ChatModel {

    /*
     * NOT: burada once @ConditionalOnBean(ChatClient.Builder.class) yazilmisti ve
     * bean SESSIZCE hic kaydolmadi -- hata da vermedi, model listesinde de
     * gorunmedi. Sebep sudur: @ConditionalOnBean, bilesen taramasindaki siniflar
     * icin GUVENILIR DEGILDIR. Bilesen taramasi otomatik yapilandirmadan ONCE
     * calisir, dolayisiyla kosul degerlendirilirken ChatClient.Builder henuz
     * ortada yoktur ve kosul her zaman false doner.
     *
     * @ConditionalOnBean yalnizca otomatik yapilandirma siniflari icinde,
     * @Bean metotlarinda anlamlidir. Burada starter zaten zorunlu bir
     * bagimlilik oldugu icin kosula gerek yok.
     */

    private final ChatClient chatClient;
    private final List<ToolCallback> callbacks;

    public SpringAiChatModel(ChatClient.Builder builder, List<Tool> tools) {
        this.chatClient = builder.build();
        this.callbacks = tools.stream().map(SpringAiChatModel::toCallback).toList();
    }

    @Override
    public String name() {
        return "spring-ai";
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    @Override
    public ModelReply reply(List<Message> conversation, List<ToolSpec> tools, String apiKey) {
        String question = conversation.stream()
                .filter(message -> message.role() == Message.Role.USER)
                .map(Message::text)
                .findFirst()
                .orElse("");

        // Arac cagirma dongusu bu cagrinin ICINDE donuyor. Disariya yalnizca
        // nihai metin cikiyor; bu yuzden hep "arac istemiyorum" diyen bir
        // cevap donuyoruz ve Agent tek turda bitiriyor.
        String answer = chatClient.prompt()
                .user(question)
                .toolCallbacks(callbacks)
                .call()
                .content();

        return ModelReply.answer(answer == null ? "" : answer);
    }

    /** Kendi {@link Tool} arayüzümüzü Spring AI'ın beklediği şekle çevirir. */
    private static ToolCallback toCallback(Tool tool) {
        ToolSpec spec = tool.spec();
        Function<Map<String, Object>, String> function = tool::execute;

        return FunctionToolCallback.builder(spec.name(), function)
                .description(spec.description())
                .inputType(Map.class)
                .build();
    }
}
