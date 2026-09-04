package com.anadoluair.flight.bookingservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Gerçek Redis'e karşı çalışan booking testleri için ortak temel.
 *
 * <p><b>Neden Redis sahtelenmiyor?</b> Saga durumu Redis'te yaşar; onu mock'lamak,
 * test edilmek istenen mekanizmayı test dışı bırakır. Daha önce test profili Redis
 * auto-configuration'ı dışlıyordu, ancak {@code SagaStateRepository} {@code RedisTemplate}
 * olmadan oluşturulamadığı için context hiç ayağa kalkmıyor ve <b>tüm test sınıfı
 * sessizce çalışmıyordu</b>.
 *
 * <p>Container tüm test sınıfları için bir kez açılır.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractBookingTest {

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
