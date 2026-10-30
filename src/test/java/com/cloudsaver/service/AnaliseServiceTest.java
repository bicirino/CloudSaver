package com.cloudsaver.service;

/*
  Arquivo para testes unitários do serviço de análise do CloudSaver

*/

import com.cloudsaver.dto.ResultadoAnalise;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AnaliseServiceTest {

    private final AnaliseService service = new AnaliseService();

    // Testando com JSON hardcoded para simular a entrada de dados de instâncias EC2
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

    @Test
    void deveAnalisarInventarioComVariosServicos() {
        String inventario = """
                {
                  "ec2Instances": [
                    { "InstanceId": "i-running", "InstanceType": "t3.micro", "State": "running" },
                    { "InstanceId": "i-stopped", "InstanceType": "t3.micro", "State": "stopped" }
                  ],
                  "ebsVolumes": [
                    { "VolumeId": "vol-001", "VolumeType": "gp3", "Size": 100, "State": "available" }
                  ],
                  "elasticIps": [
                    { "AllocationId": "eipalloc-001", "PublicIp": "203.0.113.10" }
                  ]
                }
                """;

        ResultadoAnalise resultado = service.analisar(new MockMultipartFile(
                "file", "resources.json", "application/json", inventario.getBytes()));

        assertThat(resultado.getTotalRecursos()).isEqualTo(4);
        assertThat(resultado.getTotalParadas()).isEqualTo(3);
        assertThat(resultado.getRecursos()).extracting("servico")
                .containsExactly("EC2", "EC2", "EBS", "Elastic IP");
        // running EC2 (7.59) + EBS (8.00) + IPv4 (3.65); stopped compute is zero.
        assertThat(resultado.getCustoMensal()).isEqualByComparingTo(new BigDecimal("19.24"));
    }

    @Test
    void deveIgnorarEnvelopesDeColecaoSemRecursos() {
        String inventario = """
                {
                  "ec2Instances": [
                    {
                      "value": [
                        {
                          "InstanceId": "i-running",
                          "InstanceType": "t3.micro",
                          "State": "running"
                        }
                      ],
                      "Count": 1
                    }
                  ],
                  "ebsVolumes": [
                    { "value": [], "Count": 0 }
                  ],
                  "ebsSnapshots": [
                    { "value": [], "Count": 0 }
                  ],
                  "elasticIps": [
                    { "value": [], "Count": 0 }
                  ]
                }
                """;

        ResultadoAnalise resultado = service.analisar(new MockMultipartFile(
                "file", "resources.json", "application/json", inventario.getBytes()));

        assertThat(resultado.getTotalRecursos()).isEqualTo(1);
        assertThat(resultado.getRecursos()).extracting("id")
                .containsExactly("i-running");
    }

    @Test
    void deveReconhecerCamposPadronizadosDoInventarioEManterCustosInformados() {
        String inventario = """
                {
                  "resources": [
                    {
                      "resourceId": "i-0a1b2c3d4e5f6g7h8",
                      "resourceType": "EC2",
                      "status": "running",
                      "monthly_cost": 121.18
                    },
                    {
                      "resource_id": "db-prod",
                      "resource_type": "RDS",
                      "status": "available",
                      "costUsd": 15.50
                    }
                  ]
                }
                """;

        ResultadoAnalise resultado = service.analisar(new MockMultipartFile(
                "file", "resources.json", "application/json", inventario.getBytes()));

        assertThat(resultado.getRecursos()).extracting("id")
                .containsExactly("i-0a1b2c3d4e5f6g7h8", "db-prod");
        assertThat(resultado.getRecursos()).extracting("custoMensal")
                .containsExactly(new BigDecimal("121.18"), new BigDecimal("15.50"));
        assertThat(resultado.getCustoMensal()).isEqualByComparingTo(new BigDecimal("136.68"));
    }

    @Test
    void deveInterpretarCustoMonetarioEValorAninhado() {
        String inventario = """
                {
                  "resources": [
                    {
                      "resourceId": "resource-1",
                      "monthlyCost": "$121.18",
                      "status": "running"
                    },
                    {
                      "resourceId": "resource-2",
                      "cost": { "monthly": 12.50 },
                      "status": "available"
                    }
                  ]
                }
                """;

        ResultadoAnalise resultado = service.analisar(new MockMultipartFile(
                "file", "resources.json", "application/json", inventario.getBytes()));

        assertThat(resultado.getRecursos()).extracting("custoMensal")
                .containsExactly(new BigDecimal("121.18"), new BigDecimal("12.50"));
        assertThat(resultado.getCustoMensal()).isEqualByComparingTo(new BigDecimal("133.68"));
    }
}
