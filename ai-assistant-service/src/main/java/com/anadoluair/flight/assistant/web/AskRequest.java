package com.anadoluair.flight.assistant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param question kullanıcının yeni sorusu
 * @param model    kullanılacak model adı; boş bırakılırsa varsayılan kullanılır
 * @param history  bu sohbetteki önceki turlar; boş bırakılabilir
 */
public record AskRequest(
        @NotBlank(message = "Soru bos olamaz")
        @Size(max = 500, message = "Soru en fazla 500 karakter olabilir")
        String question,

        String model,

        /*
         * Sohbet gecmisi ISTEMCIDE tutulur ve her istekte geri gonderilir.
         *
         * Sebebi: servis durumsuz kalsin. Gecmisi sunucuda tutmak oturum
         * gerektirir; oturum tutan servis yatayda cogaltilirken isteklerin ayni
         * ornege gitmesini ya da paylasilan bir oturum deposunu zorunlu kilar.
         * Bu proje sipariste de ayni tercihi yapti (JWT, stateless).
         *
         * Bedeli: her istek buyur ve istemci gecmisi bozabilir. Model zaten
         * gecmisi dogru kabul eder; bu yuzden gecmis bir GIRDIDIR, guvenlik
         * siniri degildir. Yetki denetimi hala token'a bakar, gecmise degil.
         */
        @Size(max = 20, message = "Gecmis en fazla 20 tur olabilir")
        List<@Valid Turn> history) {

    /**
     * Sohbetteki tek bir tur.
     *
     * <p>{@code text} uzunlugu sinirli. Onceden yalnizca {@code question} 500
     * karaktere sinirliydi ve ayni veri {@code history} uzerinden sinirsizca
     * gonderilebiliyordu -- yani soru icin konan kontrolun bir anlami kalmiyordu.
     * Gecmis sunucuda tutulmuyor ama istek boyunca bellekte durur ve modele
     * aktarilir.
     */
    public record Turn(
            boolean fromUser,

            @Size(max = 2000, message = "Gecmisteki bir tur en fazla 2000 karakter olabilir")
            String text) {
    }

    public List<Turn> historyOrEmpty() {
        return history == null ? List.of() : history;
    }
}
