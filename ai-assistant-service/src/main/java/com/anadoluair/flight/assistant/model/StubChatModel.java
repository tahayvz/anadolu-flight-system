package com.anadoluair.flight.assistant.model;

import com.anadoluair.flight.assistant.agent.Message;
import com.anadoluair.flight.assistant.agent.ModelReply;
import com.anadoluair.flight.assistant.agent.ToolCall;
import com.anadoluair.flight.assistant.tool.ToolSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ağa çıkmayan, sonucu her zaman aynı olan model. VARSAYILAN budur.
 *
 * <p><b>Neden varsayılan bir sahte model var?</b>
 *
 * <ul>
 *   <li>Depoyu indiren herkes uygulamayı hemen çalıştırabilsin diye. API anahtarı
 *       aramak, hesap açmak, model indirmek gerekmez. {@code docker compose up}
 *       yeter.</li>
 *   <li>CI gerçek bir modele bağlanamaz: anahtar yok, para gider, ve cevap her
 *       koşuda değişir. Testler bu modelle çalışır, sonuç kesindir.</li>
 * </ul>
 *
 * <p>Gerçek bir modelin yerini tutmaz ve tutmaya çalışmaz. Yaptığı iş, sorudaki
 * uçuş numarasını veya PNR'ı görüp doğru aracı bir kez çağırmak, sonra aracın
 * sonucunu cevap olarak vermektir. Böylece akışın tamamı gerçekten çalışır.
 */
@Component
public class StubChatModel implements ChatModel {

    private static final Pattern FLIGHT_NUMBER = Pattern.compile("\\b([A-Z]{2}\\d{1,4})\\b");
    private static final Pattern PNR = Pattern.compile("\\b([A-Z0-9]{6})\\b");

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    @Override
    public ModelReply reply(List<Message> conversation, List<ToolSpec> tools, String apiKey) {
        // Bir arac zaten calistiysa, sonucunu cevap olarak veriyoruz.
        Message lastToolResult = lastToolResult(conversation);
        if (lastToolResult != null) {
            return ModelReply.answer(lastToolResult.text());
        }

        String question = firstUserMessage(conversation).toUpperCase();

        Matcher flight = FLIGHT_NUMBER.matcher(question);
        if (flight.find()) {
            String tool = question.contains("REZERV") || question.contains("BOOK")
                    ? "flight_bookable"
                    : "flight_status";
            return ModelReply.callTool(new ToolCall(tool, Map.of("flightNumber", flight.group(1))));
        }

        Matcher pnr = PNR.matcher(question);
        if (pnr.find()) {
            return ModelReply.callTool(new ToolCall("booking_lookup", Map.of("pnr", pnr.group(1))));
        }

        return ModelReply.answer(
                "Bu ornek model yalnizca ucus numarasi (ornek ZZ123) veya PNR (ornek ABC123) "
                        + "iceren sorulari yanitlar. Gercek bir model baglamak icin README'ye bak.");
    }

    private Message lastToolResult(List<Message> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            Message message = conversation.get(i);
            if (message.role() == Message.Role.TOOL) {
                return message;
            }
        }
        return null;
    }

    private String firstUserMessage(List<Message> conversation) {
        return conversation.stream()
                .filter(message -> message.role() == Message.Role.USER)
                .map(Message::text)
                .findFirst()
                .orElse("");
    }
}
