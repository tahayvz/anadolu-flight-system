package com.anadoluair.flight.assistant.web;

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
