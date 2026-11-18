package com.cloudsaver.service;

import com.cloudsaver.dto.ResultadoAnalise;
import com.cloudsaver.dto.ScanDto;
import com.cloudsaver.dto.ScanStatsDto;
import com.cloudsaver.entity.ScanEntity;
import com.cloudsaver.repository.ScanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScanPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ScanPersistenceService.class);

    private final ScanRepository scanRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScanPersistenceService(ScanRepository scanRepository) {
        this.scanRepository = scanRepository;
    }

    @Transactional
    public ResultadoAnalise salvarScan(ResultadoAnalise resultado, String userId, String nomeArquivo) {
        if (resultado == null || userId == null || userId.isBlank()) {
            return resultado;
        }

        Instant agora = Instant.now();
        String jsonResumo = "";
        try {
            jsonResumo = objectMapper.writeValueAsString(resultado);
        } catch (Exception e) {
            log.error("Erro ao serializar resultado da análise para JSON: {}", e.getMessage());
        }

        ScanEntity entity = ScanEntity.builder()
                .userId(userId)
                .createdAt(agora)
                .totalRecursos(resultado.getTotalRecursos())
                .totalParadas(resultado.getTotalParadas())
                .custoMensal(resultado.getCustoMensal() != null ? resultado.getCustoMensal() : BigDecimal.ZERO)
                .nomeArquivo(nomeArquivo != null ? nomeArquivo : "resources.json")
                .recomendacao(resultado.getRecomendacao())
                .resumoJson(jsonResumo)
                .build();

        ScanEntity salvo = scanRepository.save(entity);
        log.info("Scan persistido com sucesso no banco: id={}, userId={}", salvo.getId(), userId);

        return resultado.toBuilder()
                .scanId(salvo.getId().toString())
                .createdAt(agora.toString())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ScanDto> listarPorUsuario(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }

        List<ScanEntity> scans = scanRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return scans.stream()
                .map(s -> ScanDto.builder()
                        .id(s.getId())
                        .userId(s.getUserId())
                        .createdAt(s.getCreatedAt())
                        .totalRecursos(s.getTotalRecursos())
                        .totalParadas(s.getTotalParadas())
                        .custoMensal(s.getCustoMensal())
                        .nomeArquivo(s.getNomeArquivo())
                        .recomendacao(s.getRecomendacao())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ResultadoAnalise> buscarDetalhePorIdEUsuario(UUID id, String userId) {
        if (id == null || userId == null || userId.isBlank()) {
            return Optional.empty();
        }

        return scanRepository.findByIdAndUserId(id, userId).map(entity -> {
            if (entity.getResumoJson() != null && !entity.getResumoJson().isBlank()) {
                try {
                    ResultadoAnalise parsed = objectMapper.readValue(entity.getResumoJson(), ResultadoAnalise.class);
                    return parsed.toBuilder()
                            .scanId(entity.getId().toString())
                            .createdAt(entity.getCreatedAt().toString())
                            .build();
                } catch (Exception e) {
                    log.error("Falha ao desserializar resumo JSON do scan {}: {}", id, e.getMessage());
                }
            }

            return ResultadoAnalise.builder()
                    .scanId(entity.getId().toString())
                    .createdAt(entity.getCreatedAt().toString())
                    .totalRecursos(entity.getTotalRecursos())
                    .totalParadas(entity.getTotalParadas())
                    .custoMensal(entity.getCustoMensal())
                    .recomendacao(entity.getRecomendacao())
                    .recursos(Collections.emptyList())
                    .instancias(Collections.emptyList())
                    .build();
        });
    }

    @Transactional
    public boolean excluirPorIdEUsuario(UUID id, String userId) {
        if (id == null || userId == null || userId.isBlank()) {
            return false;
        }
        Optional<ScanEntity> scan = scanRepository.findByIdAndUserId(id, userId);
        if (scan.isPresent()) {
            scanRepository.delete(scan.get());
            log.info("Scan {} excluído com sucesso pelo usuário {}", id, userId);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public ScanStatsDto calcularEstatisticas(String userId) {
        List<ScanDto> scans = listarPorUsuario(userId);
        if (scans.isEmpty()) {
            return ScanStatsDto.builder()
                    .totalScans(0)
                    .custoAtual(BigDecimal.ZERO)
                    .custoAnterior(BigDecimal.ZERO)
                    .variacaoPercentual(BigDecimal.ZERO)
                    .variacaoAbsoluta(BigDecimal.ZERO)
                    .totalRecursosAtual(0)
                    .totalParadasAtual(0)
                    .build();
        }

        ScanDto maisRecente = scans.get(0);
        BigDecimal custoAtual = maisRecente.getCustoMensal() != null ? maisRecente.getCustoMensal() : BigDecimal.ZERO;

        BigDecimal custoAnterior = custoAtual;
        BigDecimal variacaoPercentual = BigDecimal.ZERO;
        BigDecimal variacaoAbsoluta = BigDecimal.ZERO;

        if (scans.size() > 1) {
            ScanDto anterior = scans.get(1);
            custoAnterior = anterior.getCustoMensal() != null ? anterior.getCustoMensal() : BigDecimal.ZERO;
            variacaoAbsoluta = custoAtual.subtract(custoAnterior);

            if (custoAnterior.compareTo(BigDecimal.ZERO) > 0) {
                variacaoPercentual = variacaoAbsoluta
                        .multiply(BigDecimal.valueOf(100))
                        .divide(custoAnterior, 2, RoundingMode.HALF_UP);
            }
        }

        return ScanStatsDto.builder()
                .totalScans(scans.size())
                .custoAtual(custoAtual)
                .custoAnterior(custoAnterior)
                .variacaoPercentual(variacaoPercentual)
                .variacaoAbsoluta(variacaoAbsoluta)
                .totalRecursosAtual(maisRecente.getTotalRecursos())
                .totalParadasAtual(maisRecente.getTotalParadas())
                .build();
    }
}
