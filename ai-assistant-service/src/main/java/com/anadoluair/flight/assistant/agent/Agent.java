package com.anadoluair.flight.assistant.agent;

import com.anadoluair.flight.assistant.model.ChatModel;
import com.anadoluair.flight.assistant.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Araç çağırma döngüsü.
 *
 * <p>Bir dil modeli hiçbir şey çalıştıramaz. Sadece metin üretir. "Agent" denen
 * şey, modelin ürettiği metni okuyup gereken işi <b>bizim</b> yapmamızdır.
 * Döngü şudur:
 *
 * <pre>
 *   1. Konuşmayı ve elimizdeki araçların listesini modele gönder
 *   2. Model "su araci cagir" derse, aracı biz çalıştırırız
 *   3. Sonucu konuşmaya ekleyip modele geri göndeririz
 *   4. Model artık cevap yazabiliyorsa döngü biter
 * </pre>
 *
 * <p><b>Tur sınırı neden var?</b> Model 2. adımda takılabilir: aynı aracı tekrar
 * tekrar isteyebilir, ya da iki aracı sırayla döngüye sokabilir. Sınır olmazsa
 * istek hiç bitmez ve her turda bir HTTP çağrısı daha yapılır. Sınır, kullanıcıyı
 * ve arkadaki servisleri korur.
 */
@Slf4j
@Service
public class Agent {

    private final ToolRegistry tools;
    private final int maxTurns;

    public Agent(ToolRegistry tools, @Value("${assistant.max-turns}") int maxTurns) {
        this.tools = tools;
        this.maxTurns = maxTurns;
    }

    public AgentResult ask(String question, ChatModel model, String apiKey) {
        return ask(question, List.of(), model, apiKey);
    }

    /**
     * @param history önceki turlar; modele bağlam olarak verilir, sonra yeni soru eklenir
     */
    public AgentResult ask(String question, List<Message> history, ChatModel model, String apiKey) {
        List<Message> conversation = new ArrayList<>(history);
        conversation.add(Message.user(question));

        List<AgentStep> steps = new ArrayList<>();

        for (int turn = 1; turn <= maxTurns; turn++) {
            ModelReply reply = model.reply(conversation, tools.specs(), apiKey);

            if (!reply.wantsTool()) {
                log.info("Agent {} turda cevapladi, {} arac cagrisi", turn, steps.size());
                return AgentResult.answered(reply.text(), steps);
            }

            ToolCall call = reply.toolCall();
            String result = tools.run(call);
            steps.add(new AgentStep(call.tool(), call.arguments(), result));

            conversation.add(Message.assistant("Arac cagriliyor: " + call.tool()));
            conversation.add(Message.toolResult(call.tool(), result));
        }

        log.warn("Agent {} tur sonunda cevaba ulasamadi", maxTurns);
        return AgentResult.ranOutOfTurns(steps);
    }
}
