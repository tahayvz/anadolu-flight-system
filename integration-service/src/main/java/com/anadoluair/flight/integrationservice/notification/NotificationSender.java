package com.anadoluair.flight.integrationservice.notification;

/**
 * Bildirim gonderme sozlesmesi.
 * <p>
 * Arayuz olmasinin sebebi: gonderim kanali degisebilir (SMTP bugun, saglayici API'si
 * yarin) ve testlerde gercek posta sunucusu yerine sahte bir gonderici konabilir.
 * Tuketici hangi kanalin kullanildigini bilmez.
 */
public interface NotificationSender {

    void send(BookingNotification notification);
}
