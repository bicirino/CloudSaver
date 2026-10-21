package com.cloudsaver.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

// JSON de resposta da análise.
@Getter
@Builder
public class ResultadoAnalise {

    private int totalParadas;
    private BigDecimal custoMensal;
    private String recomendacao;
    private List<InstanciaResumo> instancias;

    @Getter
    @Builder
    public static class InstanciaResumo {
        private String id;
        private String tipo;
        private BigDecimal custoMensal;
    }
}
