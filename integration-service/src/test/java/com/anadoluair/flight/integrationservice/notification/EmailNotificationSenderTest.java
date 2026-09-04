package com.anadoluair.flight.integrationservice.notification;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gonderimin GERCEKTEN calistigini dogrular.
 *
 * <p>GreenMail, testin icinde calisan gercek bir SMTP sunucusudur. Mock kullanilsaydi
 * yalnizca "mailSender.send cagrildi mi" dogrulanirdi; burada mesajin sunucuya ulastigi,
 * alicisinin, konusunun ve govdesinin dogru oldugu dogrulanir. Dis bir servise ihtiyac
 * yoktur, Docker da gerekmez.
 */
@DisplayName("E-posta gonderimi")
class EmailNotificationSenderTest {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    private EmailNotificationSender sender;

    @BeforeEach
    void setUp() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(smtp.getSmtp().getPort());

        sender = new EmailNotificationSender(mailSender, "no-reply@anadoluair.example");
    }

    private BookingNotification notification(String recipient) {
        return new BookingNotification(recipient, "E-biletiniz hazir - ABC123",
                "Rezervasyon kodu : ABC123\nUcus             : AA1234\n");
    }

    @Test
    @DisplayName("posta gercekten SMTP sunucusuna ulasir")
    void shouldDeliverMessageToSmtpServer() throws Exception {
        sender.send(notification("yolcu@ornek.example"));

        assertThat(smtp.waitForIncomingEmail(5000, 1)).isTrue();

        MimeMessage[] received = smtp.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("E-biletiniz hazir - ABC123");
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("yolcu@ornek.example");
        assertThat(received[0].getContent().toString()).contains("ABC123").contains("AA1234");
    }

    @Test
    @DisplayName("gonderen adresi yapilandirmadan gelir")
    void shouldUseConfiguredSenderAddress() throws Exception {
        sender.send(notification("yolcu@ornek.example"));
        assertThat(smtp.waitForIncomingEmail(5000, 1)).isTrue();

        assertThat(smtp.getReceivedMessages()[0].getFrom()[0].toString())
                .isEqualTo("no-reply@anadoluair.example");
    }

    @Test
    @DisplayName("alici yoksa posta gonderilmez, hata da atilmaz")
    void shouldSkipSilentlyWhenRecipientMissing() {
        sender.send(notification(null));

        // Beklenen: hicbir posta gonderilmedi ve istisna firlatilmadi
        assertThat(smtp.getReceivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("SMTP erisilemezse istisna FIRLATILMAZ")
    void shouldNotThrowWhenSmtpUnavailable() {
        JavaMailSenderImpl broken = new JavaMailSenderImpl();
        broken.setHost("localhost");
        broken.setPort(1);   // kimse dinlemiyor

        EmailNotificationSender brokenSender =
                new EmailNotificationSender(broken, "no-reply@anadoluair.example");

        // Bildirim gonderilemedigi icin olay tuketimi basarisiz sayilmamali; aksi halde
        // Kafka mesaji tekrar islenir ve ayni rezervasyon icin defalarca deneme olurdu.
        brokenSender.send(notification("yolcu@ornek.example"));
    }
}
