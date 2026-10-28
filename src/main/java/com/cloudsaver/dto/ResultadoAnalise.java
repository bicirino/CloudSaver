package com.cloudsaver.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

// JSON de resposta da análise.
@Getter
@Builder
public class ResultadoAnalise {

    private int totalRecursos;
    private int totalParadas;
    private BigDecimal custoMensal;
    private String recomendacao;
    private List<RecursoResumo> recursos;
    private List<InstanciaResumo> instancias;

    @Getter
    @Builder
    public static class RecursoResumo {
        private String id;
        private String servico;
        private String tipo;
        private String estado;
        private BigDecimal custoMensal;
        private boolean ocioso;
    }

    @Getter
    @Builder
    public static class InstanciaResumo {
        private String id;
        private String tipo;
        private BigDecimal custoMensal;
    }
}
