package com.cloudsaver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class AnaliseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AnaliseExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> requisicaoInvalida(IllegalArgumentException excecao) {
        String mensagem = excecao.getMessage() == null ? "" : excecao.getMessage();
        if (mensagem.startsWith("Arquivo grande demais")
                || mensagem.equals("O arquivo está vazio.")
                || mensagem.contains("limite de")
                || mensagem.contains("Inventário com muitos recursos")) {
            return ResponseEntity.badRequest().body(mensagem);
        }
        log.warn("Requisição de análise rejeitada: {}", mensagem);
        return ResponseEntity.badRequest().body(
                "Não foi possível processar o inventário. Verifique se o arquivo é um JSON válido "
                        + "e está dentro dos limites suportados.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<String> arquivoGrandeDemais(MaxUploadSizeExceededException excecao) {
        log.warn("Upload excedeu o limite multipart: {}", excecao.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                "Arquivo grande demais para análise. Envie um inventário menor "
                        + "(exportações típicas têm cerca de 3 KB; limite 100 KB).");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<String> erroInesperado(Exception excecao) {
        log.error("Erro inesperado na análise", excecao);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                "Ocorreu um erro interno. Tente novamente mais tarde.");
    }
}
