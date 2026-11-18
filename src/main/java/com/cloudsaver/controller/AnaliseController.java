package com.cloudsaver.controller;

import com.cloudsaver.dto.ResultadoAnalise;
import com.cloudsaver.security.SupabaseAuthenticationToken;
import com.cloudsaver.security.SupabaseUser;
import com.cloudsaver.service.AnaliseService;
import com.cloudsaver.service.ScanPersistenceService;
import com.cloudsaver.service.SupabaseAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AnaliseController {

    private final AnaliseService analiseService;
    private final ScanPersistenceService scanPersistenceService;
    private final SupabaseAuthService supabaseAuthService;

    public AnaliseController(
            AnaliseService analiseService,
            ScanPersistenceService scanPersistenceService,
            SupabaseAuthService supabaseAuthService) {
        this.analiseService = analiseService;
        this.scanPersistenceService = scanPersistenceService;
        this.supabaseAuthService = supabaseAuthService;
    }

    /**
     * Recebe o inventário JSON. Por padrão não envia dados à Groq (usarIa=false).
     * Com usarIa=true o usuário consente no envio de um resumo sanitizado à LLM.
     * Suporta /api/analise e /api/scan/upload para compatibilidade com o frontend e testes.
     */
    @PostMapping({"/analise", "/scan/upload"})
    public ResponseEntity<ResultadoAnalise> analisar(
            @RequestParam("file") MultipartFile arquivo,
            @RequestParam(value = "usarIa", defaultValue = "false") boolean usarIa) {

        String userId = resolveUserId();
        ResultadoAnalise resultado = analiseService.analisar(arquivo, usarIa);

        if (userId != null && !userId.isBlank()) {
            resultado = scanPersistenceService.salvarScan(resultado, userId, arquivo.getOriginalFilename());
        }

        return ResponseEntity.ok(resultado);
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof SupabaseAuthenticationToken token && token.getPrincipal() != null) {
            return token.getPrincipal().getId();
        }

        if (supabaseAuthService.isAuthRequired()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação requerida para submeter análises");
        }

        SupabaseUser devUser = supabaseAuthService.getFallbackDevUser();
        return devUser != null ? devUser.getId() : null;
    }
}
