package com.anadoluair.flight.bookingservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis'e doğrudan erişim için {@link RedisTemplate}.
 *
 * <p><b>Neden önbellek yapılandırmasından ayrı?</b> Bu bean saga durumunu saklamak için
 * kullanılır ({@code SagaStateRepository}) — önbelleklemeyle ilgisi yoktur. Daha önce
 * {@code RedisCacheConfig} içindeydi ve o sınıf {@code spring.cache.type=redis} koşuluna
 * bağlıydı. Önbellek kapatıldığında ({@code cache.type: none}) tüm sınıf atlanıyor,
 * {@code RedisTemplate} de onunla birlikte kayboluyor ve uygulama hiç ayağa
 * kalkmıyordu.
 *
 * <p>İki farklı sorumluluk aynı koşula bağlanmıştı. Ayrılınca önbellek bağımsız olarak
 * kapatılabiliyor, saga saklama çalışmaya devam ediyor.
 */
@Configuration
public class RedisTemplateConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Anahtarlar düz metin: redis-cli ile bakıldığında okunabilir olsun
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /** JavaTimeModule şart: saga durumu {@code Instant}/{@code LocalDate} taşır. */
    private ObjectMapper redisObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
