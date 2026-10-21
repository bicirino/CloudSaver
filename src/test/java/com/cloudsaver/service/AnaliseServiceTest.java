package com.cloudsaver.service;

import com.cloudsaver.dto.ResultadoAnalise;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AnaliseServiceTest {

    private final AnaliseService service = new AnaliseService();

    // JSON com duas instâncias: uma stopped e uma running.
    // O serviço deve identificar apenas a parada e calcular o custo correto.
    private static final String JSON = """
            [
              {
                "InstanceId": "i-abc123",
                "InstanceType": "t3.micro",
                "State": "stopped"
              },
              {
                "InstanceId": "i-xyz789",
                "InstanceType": "t3.medium",
                "State": "running"
              }
            ]
            """;

    @Test
    void deveIdentificarApenasInstanciasParadas() {
        MockMultipartFile arquivo = new MockMultipartFile("file", "ec2.json",
                "application/json", JSON.getBytes());

        ResultadoAnalise resultado = service.analisar(arquivo);

        assertThat(resultado.getTotalParadas()).isEqualTo(1);
        assertThat(resultado.getInstancias()).hasSize(1);
        assertThat(resultado.getInstancias().get(0).getId()).isEqualTo("i-abc123");
    }

    @Test
    void deveCalcularCustoMensalCorreto() {
        MockMultipartFile arquivo = new MockMultipartFile("file", "ec2.json",
                "application/json", JSON.getBytes());

        ResultadoAnalise resultado = service.analisar(arquivo);

        // t3.micro = $0.0104/h × 730h = $7.59
        BigDecimal esperado = new BigDecimal("7.59");
        assertThat(resultado.getCustoMensal()).isEqualByComparingTo(esperado);
    }

    @Test
    void semInstanciasParadas_deveDevolverCustoZero() {
        String semParadas = """
                [
                  { "InstanceId": "i-001", "InstanceType": "t3.micro", "State": "running" }
                ]
                """;
        MockMultipartFile arquivo = new MockMultipartFile("file", "ec2.json",
                "application/json", semParadas.getBytes());

        ResultadoAnalise resultado = service.analisar(arquivo);

        assertThat(resultado.getTotalParadas()).isZero();
        assertThat(resultado.getCustoMensal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
