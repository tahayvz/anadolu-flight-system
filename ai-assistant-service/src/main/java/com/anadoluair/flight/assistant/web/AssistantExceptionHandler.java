package com.anadoluair.flight.assistant.web;

import com.anadoluair.flight.assistant.model.ModelCallException;
import com.anadoluair.flight.assistant.model.UnknownModelException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AssistantExceptionHandler {

    @ExceptionHandler(UnknownModelException.class)
    public ProblemDetail unknownModel(UnknownModelException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bilinmeyen model");
        problem.setDetail(e.getMessage());
        return problem;
    }

    /**
     * Sağlayıcı çağrısı başarısız oldu.
     *
     * <p>Önce bu işleyici YOKTU ve her sağlayıcı hatası düz bir 500 olarak
     * dönüyordu. Kullanıcı geçersiz bir anahtar girdiğinde "Internal Server Error"
     * görüyordu; oysa sunucuda bir arıza yoktu, anahtar yanlıştı. Bu, hatayı
     * kullanıcının kendi tarafında aramasına yol açar.
     *
     * <p>Durum kodu bilinçli olarak ikiye ayrılıyor: anahtar hatası çağıranın
     * düzeltebileceği bir şeydir (400), sağlayıcının çökmesi değildir (502).
     */
    @ExceptionHandler(ModelCallException.class)
    public ProblemDetail modelCallFailed(ModelCallException e) {
        HttpStatus status = e.causedByCaller() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(e.causedByCaller()
                ? "Model saglayicisi istegi reddetti"
                : "Model saglayicisina ulasilamadi");
        problem.setDetail(e.reason());
        problem.setProperty("provider", e.provider());
        problem.setProperty("upstreamStatus", e.upstreamStatus());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Gecersiz istek");
        problem.setDetail(e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Istek dogrulanamadi"));
        return problem;
    }
}
