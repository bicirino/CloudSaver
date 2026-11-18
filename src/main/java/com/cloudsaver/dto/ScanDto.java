package com.cloudsaver.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class ScanDto {
    private UUID id;
    private String userId;
    private Instant createdAt;
    private int totalRecursos;
    private int totalParadas;
    private BigDecimal custoMensal;
    private String nomeArquivo;
    private String recomendacao;

    public ScanDto() {
    }

    @Builder
    public ScanDto(
            UUID id,
            String userId,
            Instant createdAt,
            int totalRecursos,
            int totalParadas,
            BigDecimal custoMensal,
            String nomeArquivo,
            String recomendacao) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.totalRecursos = totalRecursos;
        this.totalParadas = totalParadas;
        this.custoMensal = custoMensal;
        this.nomeArquivo = nomeArquivo;
        this.recomendacao = recomendacao;
    }
}
