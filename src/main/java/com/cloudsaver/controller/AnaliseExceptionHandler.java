package com.cloudsaver.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class AnaliseExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> requisicaoInvalida(IllegalArgumentException excecao) {
        return ResponseEntity.badRequest().body(excecao.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<String> arquivoGrandeDemais(MaxUploadSizeExceededException excecao) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                "Arquivo grande demais para análise. Envie um inventário menor "
                        + "(exportações típicas têm cerca de 3 KB).");
    }
}