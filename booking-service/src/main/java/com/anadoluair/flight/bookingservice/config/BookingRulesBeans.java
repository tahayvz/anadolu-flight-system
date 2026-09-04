package com.anadoluair.flight.bookingservice.config;

import com.anadoluair.flight.bookingservice.rules.BookingRulesValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Kural doğrulayıcısı ve bağımlı olduğu saat.
 * <p>
 * {@link BookingRulesValidator} bilinçli olarak Spring anotasyonu taşımaz: framework'süz
 * kalması, çıplak JUnit ile test edilebilmesini sağlar. Bean olarak bağlanması bu
 * yapılandırmanın işidir.
 */
@Configuration
public class BookingRulesBeans {

    /**
     * Uygulama saati.
     * <p>
     * Kod içinde {@code LocalDateTime.now()} çağırmak yerine enjekte edilen bir saat
     * kullanmak, zamana bağlı kuralları testte sabitlemeyi mümkün kılar.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public BookingRulesValidator bookingRulesValidator(BookingRulesConfig rules, Clock clock) {
        return new BookingRulesValidator(rules, clock);
    }
}
