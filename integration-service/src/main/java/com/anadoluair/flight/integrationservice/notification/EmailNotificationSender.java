package com.anadoluair.flight.integrationservice.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Bildirimleri SMTP uzerinden e-posta olarak gonderir.
 * <p>
 * Lokal gelistirmede docker-compose icindeki <b>MailHog</b> kullanilir: gercek bir SMTP
 * sunucusu gibi davranir ama postayi disari cikarmaz, web arayuzunde
 * (http://localhost:8025) gosterir. Boylece gonderim gercekten calisir, kimseye posta
 * gitmez ve sonuc gozle dogrulanabilir.
 */
@Slf4j
@Component
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationSender(JavaMailSender mailSender,
                                   @Value("${anadolu.notification.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(BookingNotification notification) {
        if (!notification.isDeliverable()) {
            log.warn("Bildirim gonderilemedi, alici adresi yok: subject={}", notification.subject());
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(notification.recipient());
        message.setSubject(notification.subject());
        message.setText(notification.body());

        try {
            mailSender.send(message);
            log.info("Bildirim gonderildi: to={}, subject={}",
                    notification.recipient(), notification.subject());
        } catch (MailException e) {
            // Bildirim gonderilemedigi icin olay tuketimi basarisiz sayilmaz: rezervasyon
            // gecerlidir, yalnizca haber verilemedi. Istisna firlatilsaydi Kafka mesaji
            // tekrar islenir ve ayni rezervasyon icin defalarca posta denemesi olurdu.
            log.error("Bildirim gonderilemedi: to={}, subject={}",
                    notification.recipient(), notification.subject(), e);
        }
    }
}
