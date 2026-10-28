package com.cloudsaver.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Representa um recurso da AWS.
*/
public record RecursoAws(
        String id,
        String servico,
        String tipo,
        String estado,
        BigDecimal custoMensal,
        boolean ocioso,
        JsonNode dados
) {
}
