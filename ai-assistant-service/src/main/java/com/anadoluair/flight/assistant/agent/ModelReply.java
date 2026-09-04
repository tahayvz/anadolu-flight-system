package com.anadoluair.flight.assistant.agent;

/**
 * Modelin tek bir turdaki cevabı. İki şıktan biridir:
 *
 * <ul>
 *   <li>Kullanıcıya yazılacak metin ({@code text} dolu, {@code toolCall} boş)</li>
 *   <li>Bir araç çağırma isteği ({@code toolCall} dolu)</li>
 * </ul>
 */
public record ModelReply(String text, ToolCall toolCall) {

    public static ModelReply answer(String text) {
        return new ModelReply(text, null);
    }

    public static ModelReply callTool(ToolCall call) {
        return new ModelReply(null, call);
    }

    public boolean wantsTool() {
        return toolCall != null;
    }
}
