package com.cloudsaver.controller;

import com.cloudsaver.dto.ResultadoAnalise;
import com.cloudsaver.dto.ScanDto;
import com.cloudsaver.dto.ScanStatsDto;
import com.cloudsaver.security.SupabaseAuthenticationToken;
import com.cloudsaver.security.SupabaseUser;
import com.cloudsaver.service.ScanPersistenceService;
import com.cloudsaver.service.SupabaseAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class HistoryController {

    private final ScanPersistenceService scanPersistenceService;
    private final SupabaseAuthService supabaseAuthService;

    public HistoryController(ScanPersistenceService scanPersistenceService, SupabaseAuthService supabaseAuthService) {
        this.scanPersistenceService = scanPersistenceService;
        this.supabaseAuthService = supabaseAuthService;
    }

    /**
     * Retorna a lista de scans anteriores do usuário autenticado.
     * Suporta /api/history e /api/scan/history.
     */
    @GetMapping({"/history", "/scan/history"})
    public ResponseEntity<List<ScanDto>> listarHistorico() {
        String userId = obterUserIdObrigatorio();
        List<ScanDto> scans = scanPersistenceService.listarPorUsuario(userId);
        return ResponseEntity.ok(scans);
    }

    /**
     * Retorna estatísticas comparativas de evolução de custos para o dashboard.
     */
    @GetMapping({"/history/stats", "/scan/history/stats"})
    public ResponseEntity<ScanStatsDto> obterEstatisticas() {
        String userId = obterUserIdObrigatorio();
        ScanStatsDto stats = scanPersistenceService.calcularEstatisticas(userId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Retorna os detalhes completos de uma análise passada (inclusive recomendações IA).
     */
    @GetMapping({"/history/{id}", "/scan/history/{id}"})
    public ResponseEntity<ResultadoAnalise> obterDetalheScan(@PathVariable("id") UUID id) {
        String userId = obterUserIdObrigatorio();
        return scanPersistenceService.buscarDetalhePorIdEUsuario(id, userId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Análise não encontrada"));
    }

    /**
     * Permite excluir um scan do histórico do usuário.
     */
    @DeleteMapping({"/history/{id}", "/scan/history/{id}"})
    public ResponseEntity<Void> excluirScan(@PathVariable("id") UUID id) {
        String userId = obterUserIdObrigatorio();
        boolean excluido = scanPersistenceService.excluirPorIdEUsuario(id, userId);
        if (!excluido) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Análise não encontrada para exclusão");
        }
        return ResponseEntity.noContent().build();
    }

    private String obterUserIdObrigatorio() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof SupabaseAuthenticationToken token && token.getPrincipal() != null) {
            return token.getPrincipal().getId();
        }

        if (supabaseAuthService.isAuthRequired()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado. Faça login via Supabase.");
        }

        SupabaseUser devUser = supabaseAuthService.getFallbackDevUser();
        return devUser != null ? devUser.getId() : null;
    }
}
