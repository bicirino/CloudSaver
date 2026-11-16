package com.cloudsaver.controller;

import com.cloudsaver.dto.ResultadoAnalise;
import com.cloudsaver.service.AnaliseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class AnaliseController {

    private final AnaliseService analiseService;

    public AnaliseController(AnaliseService analiseService) {
        this.analiseService = analiseService;
    }

    /**
     * Recebe o inventário JSON. Por padrão não envia dados à Groq (usarIa=false).
     * Com usarIa=true o usuário consente no envio de um resumo sanitizado à LLM.
     */
    @PostMapping("/analise")
    public ResponseEntity<ResultadoAnalise> analisar(
            @RequestParam("file") MultipartFile arquivo,
            @RequestParam(value = "usarIa", defaultValue = "false") boolean usarIa) {
        return ResponseEntity.ok(analiseService.analisar(arquivo, usarIa));
    }
}
