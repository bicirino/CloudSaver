package com.cloudsaver.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ScanStatsDto {
    private int totalScans;
    private BigDecimal custoAtual;
    private BigDecimal custoAnterior;
    private BigDecimal variacaoPercentual;
    private BigDecimal variacaoAbsoluta;
    private int totalRecursosAtual;
    private int totalParadasAtual;

    public ScanStatsDto() {
    }

    @Builder
    public ScanStatsDto(
            int totalScans,
            BigDecimal custoAtual,
            BigDecimal custoAnterior,
            BigDecimal variacaoPercentual,
            BigDecimal variacaoAbsoluta,
            int totalRecursosAtual,
            int totalParadasAtual) {
        this.totalScans = totalScans;
        this.custoAtual = custoAtual;
        this.custoAnterior = custoAnterior;
        this.variacaoPercentual = variacaoPercentual;
        this.variacaoAbsoluta = variacaoAbsoluta;
        this.totalRecursosAtual = totalRecursosAtual;
        this.totalParadasAtual = totalParadasAtual;
    }
}
