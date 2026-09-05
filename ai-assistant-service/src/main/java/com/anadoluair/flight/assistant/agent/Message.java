package com.anadoluair.flight.assistant.agent;

/**
 * Konuşmadaki tek bir satır.
 *
 * <p>Bir sohbet modeli hafızasızdır. Her istekte konuşmanın tamamını yeniden
 * göndeririz. Bu tip o konuşmanın bir satırıdır.
 *
 * @param role  satırı kimin yazdığı
 * @param text  satırın içeriği
 * @param tool  yalnızca {@link Role#TOOL} satırlarında dolu: sonucu üreten aracın adı
 */
public record Message(Role role, String text, String tool) {

    public enum Role {
        /** Modele en baştan verilen kurallar. Kullanıcıdan gelmez, biz yazarız. */
        SYSTEM,
        /** Kullanıcının sorusu. */
        USER,
        /** Modelin cevabı ya da "şu aracı çağır" isteği. */
        ASSISTANT,
        /** Bir aracın döndürdüğü sonuç. Modele geri beslenir. */
        TOOL
    }

    public static Message system(String text) {
        return new Message(Role.SYSTEM, text, null);
    }

    public static Message user(String text) {
        return new Message(Role.USER, text, null);
    }

    public static Message assistant(String text) {
        return new Message(Role.ASSISTANT, text, null);
    }

    public static Message toolResult(String tool, String text) {
        return new Message(Role.TOOL, text, tool);
    }
}
