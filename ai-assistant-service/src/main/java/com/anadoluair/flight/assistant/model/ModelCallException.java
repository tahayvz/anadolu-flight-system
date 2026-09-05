package com.anadoluair.flight.assistant.model;

/**
 * Bir dil modeli sağlayıcısına yapılan çağrı başarısız oldu.
 *
 * <p>Bu hata dışarıya <b>anlaşılır</b> çıkmalı. Sağlayıcının söylediği şey
 * kullanıcının bilmesi gereken şeydir: "anahtar geçersiz", "kota doldu",
 * "model bulunamadı". Bunu yutup 500 dönmek, kullanıcıyı karanlıkta bırakır ve
 * sorunu kendi kodunda aramaya iter.
 *
 * @param provider    hangi sağlayıcı ("gemini")
 * @param upstreamStatus sağlayıcının döndüğü HTTP kodu; bağlanılamadıysa 0
 * @param reason      sağlayıcının açıklaması
 */
public class ModelCallException extends RuntimeException {

    private final String provider;
    private final int upstreamStatus;
    private final String reason;

    public ModelCallException(String provider, int upstreamStatus, String reason, Throwable cause) {
        super("%s cagrisi basarisiz (durum %d): %s".formatted(provider, upstreamStatus, reason), cause);
        this.provider = provider;
        this.upstreamStatus = upstreamStatus;
        this.reason = reason;
    }

    public String provider() {
        return provider;
    }

    public int upstreamStatus() {
        return upstreamStatus;
    }

    public String reason() {
        return reason;
    }

    /**
     * Hata çağıranın verdiği anahtardan mı kaynaklanıyor?
     *
     * <p>Ayrım önemli: anahtar yanlışsa bu <b>isteği yapanın</b> düzeltebileceği bir
     * şeydir ve 400 dönmeliyiz. Sağlayıcı çökmüşse yapabileceği bir şey yoktur ve
     * 502 dönmeliyiz. İkisine aynı kodu vermek, kullanıcıya "sende mi bende mi?"
     * sorusunu cevaplatmaz.
     */
    public boolean causedByCaller() {
        return upstreamStatus == 400 || upstreamStatus == 401 || upstreamStatus == 403;
    }
}
