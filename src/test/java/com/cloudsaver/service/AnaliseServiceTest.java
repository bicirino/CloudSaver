package com.cloudsaver.service;

/*
  Arquivo para testes unitários do serviço de análise do CloudSaver

*/

import com.cloudsaver.dto.ResultadoAnalise;
import com.cloudsaver.dto.RecursoAws;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnaliseServiceTest {

    private final AnaliseService service = new AnaliseService();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    void deveMapearEstadoDeRdsEElasticIpECustoDeM52xlarge() {
        String inventario = """
                {
                  "ec2Instances": [
                    { "InstanceId": "i-grande", "InstanceType": "m5.2xlarge", "State": { "Name": "running" } }
                  ],
                  "rdsInstances": [
                    { "DBInstanceIdentifier": "prod-db", "DBInstanceClass": "db.t3.micro", "DBInstanceStatus": "available" },
                    { "DBInstanceIdentifier": "old-db", "DBInstanceClass": "db.t3.micro", "DBInstanceStatus": "stopped" }
                  ],
                  "elasticIps": [
                    { "AllocationId": "eipalloc-livre", "AssociationId": null },
                    { "AllocationId": "eipalloc-usado", "AssociationId": "eipassoc-001" }
                  ]
                }
                """;

        ResultadoAnalise resultado = service.analisar(new MockMultipartFile(
                "file", "resources.json", "application/json", inventario.getBytes()));

        assertThat(resultado.getRecursos()).extracting("estado")
                .containsExactly("running", "unassociated", "associated", "available", "stopped");
        assertThat(resultado.getRecursos()).extracting("ocioso")
                .containsExactly(false, true, false, false, true);
        // IPv4 público é cobrado mesmo quando associado.
        assertThat(resultado.getRecursos()).extracting("custoMensal")
                .containsExactly(new BigDecimal("280.32"), new BigDecimal("3.65"), new BigDecimal("3.65"),
                        new BigDecimal("12.41"), BigDecimal.ZERO);
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
    void deveReconhecerFormatoEc2ResourcesJson() throws Exception {
        byte[] json = getClass().getResourceAsStream("/ec2_resources_sample.json").readAllBytes();
        ResultadoAnalise resultado = service.analisar(new MockMultipartFile(
                "file", "ec2_resources.json", "application/json", json));

        assertThat(resultado.getRecursos()).extracting("id")
                .containsExactly("i-0a1b2c3d4e5f6g7h8", "i-0987654321fedcba0", "vol-0f1e2d3c4b5a69788");
        assertThat(resultado.getRecursos()).extracting("servico")
                .containsExactly("EC2", "EC2", "EBS");
        assertThat(resultado.getRecursos()).extracting("tipo")
                .containsExactly("t3.xlarge", "t3.medium", "gp2");
        assertThat(resultado.getRecursos()).extracting("custoMensal")
                .containsExactly(new BigDecimal("121.18"), new BigDecimal("30.40"), new BigDecimal("50.00"));
        assertThat(resultado.getCustoMensal()).isEqualByComparingTo(new BigDecimal("201.58"));
    }

    @Test
    void promptDeveRestringirEncerramentoARecursosOciosos() {
        String prompt = service.montarPrompt(List.of(
                new RecursoAws("i-running", "EC2", "t3.micro", "running",
                        BigDecimal.ZERO, false, objectMapper.createObjectNode()),
                new RecursoAws("i-stopped", "EC2", "t3.micro", "stopped",
                        BigDecimal.ZERO, true, objectMapper.createObjectNode())
        ), BigDecimal.ZERO);

        assertThat(prompt).contains("i-running (t3.micro, running, ocioso: false)");
        assertThat(prompt).contains("i-stopped (t3.micro, stopped, ocioso: true)");
        assertThat(prompt).contains("Nunca recomende parar, excluir ou encerrar um recurso com \"ocioso: false\"");
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

    @Test
    void promptDeveUsarSomenteMetricasPresentesNoInventario() throws Exception {
        RecursoAws recurso = new RecursoAws(
                "i-prod",
                "EC2",
                "t3.xlarge",
                "running",
                new BigDecimal("121.18"),
                false,
                objectMapper.readTree("""
                        {
                          "cpu_utilization_avg_percent": 1.1,
                          "memory_utilization_avg_percent": 4.5
                        }
                        """));

        String prompt = service.montarPrompt(List.of(recurso), new BigDecimal("121.18"));

        assertThat(prompt).contains("Métrica CPU (cpu_utilization_avg_percent): 1.1%");
        assertThat(prompt).contains("Métrica memória (memory_utilization_avg_percent): 4.5%");
        assertThat(prompt).doesNotContain("Métrica GPU");
    }

    @Test
    void deveSanitizarLimiteDeUsoDaGroq() {
        HttpClientErrorException erro = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                "{\"error\":\"Rate limit reached\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        String mensagem = service.mensagemFalhaGroq(erro);

        assertThat(mensagem).contains("limite de uso da IA");
        assertThat(mensagem).doesNotContain("api.groq.com", "Rate limit", "tokens");
    }

    @Test
    void deveCalcularCustoDeRdsR5DoisXlarge() {
        String inventario = """
                {
                  "rdsInstances": [
                    {
                      "DBInstanceIdentifier": "qa-db",
                      "DBInstanceClass": "db.r5.2xlarge",
                      "DBInstanceStatus": "available"
                    }
                  ]
                }
                """;

        ResultadoAnalise resultado = service.analisar(new MockMultipartFile(
                "file", "resources.json", "application/json", inventario.getBytes()));

        assertThat(resultado.getCustoMensal()).isEqualByComparingTo(new BigDecimal("730.00"));
    }

    @Test
    void deveSanitizarErroDeLimiteDaGroq() {
        HttpClientErrorException erro = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                "{\"error\":\"Rate limit reached\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        String mensagem = service.mensagemFalhaGroq(erro);

        assertThat(mensagem).contains("limite de requisições");
        assertThat(mensagem).doesNotContain("Rate limit", "api.groq.com", "tokens");
    }

    @Test
    void deveSanitizarOutrosErrosDaGroq() {
        String mensagem = service.mensagemFalhaGroq(
                new IllegalStateException("resposta interna com URL e tokens"));

        assertThat(mensagem).contains("Não foi possível gerar a recomendação");
        assertThat(mensagem).doesNotContain("URL", "tokens", "resposta interna");
    }
}
