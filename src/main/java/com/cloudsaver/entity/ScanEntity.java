package com.cloudsaver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scans")
@Getter
@Setter
public class ScanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "total_recursos", nullable = false)
    private int totalRecursos;

    @Column(name = "total_paradas", nullable = false)
    private int totalParadas;

    @Column(name = "custo_mensal", precision = 12, scale = 4, nullable = false)
    private BigDecimal custoMensal;

    @Column(name = "nome_arquivo")
    private String nomeArquivo;

    @Column(name = "recomendacao", columnDefinition = "TEXT")
    private String recomendacao;

    @Column(name = "resumo_json", columnDefinition = "TEXT")
    private String resumoJson;

    public ScanEntity() {
    }

    @Builder
    public ScanEntity(
            UUID id,
            String userId,
            Instant createdAt,
            int totalRecursos,
            int totalParadas,
            BigDecimal custoMensal,
            String nomeArquivo,
            String recomendacao,
            String resumoJson) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.totalRecursos = totalRecursos;
        this.totalParadas = totalParadas;
        this.custoMensal = custoMensal;
        this.nomeArquivo = nomeArquivo;
        this.recomendacao = recomendacao;
        this.resumoJson = resumoJson;
    }
}
