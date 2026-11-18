package com.cloudsaver.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

// JSON de resposta da análise.
@Getter
@Setter
public class ResultadoAnalise {

    private String scanId;
    private String createdAt;
    private int totalRecursos;
    private int totalParadas;
    private BigDecimal custoMensal;
    private String recomendacao;
    private List<RecursoResumo> recursos;
    private List<InstanciaResumo> instancias;

    public ResultadoAnalise() {
    }

    @Builder(toBuilder = true)
    public ResultadoAnalise(
            String scanId,
            String createdAt,
            int totalRecursos,
            int totalParadas,
            BigDecimal custoMensal,
            String recomendacao,
            List<RecursoResumo> recursos,
            List<InstanciaResumo> instancias) {
        this.scanId = scanId;
        this.createdAt = createdAt;
        this.totalRecursos = totalRecursos;
        this.totalParadas = totalParadas;
        this.custoMensal = custoMensal;
        this.recomendacao = recomendacao;
        this.recursos = recursos;
        this.instancias = instancias;
    }

    @Getter
    @Setter
    public static class RecursoResumo {
        private String id;
        private String servico;
        private String tipo;
        private String estado;
        private BigDecimal custoMensal;
        private boolean ocioso;

        public RecursoResumo() {
        }

        @Builder
        public RecursoResumo(
                String id,
                String servico,
                String tipo,
                String estado,
                BigDecimal custoMensal,
                boolean ocioso) {
            this.id = id;
            this.servico = servico;
            this.tipo = tipo;
            this.estado = estado;
            this.custoMensal = custoMensal;
            this.ocioso = ocioso;
        }
    }

    @Getter
    @Setter
    public static class InstanciaResumo {
        private String id;
        private String tipo;
        private BigDecimal custoMensal;

        public InstanciaResumo() {
        }

        @Builder
        public InstanciaResumo(String id, String tipo, BigDecimal custoMensal) {
            this.id = id;
            this.tipo = tipo;
            this.custoMensal = custoMensal;
        }
    }
}
