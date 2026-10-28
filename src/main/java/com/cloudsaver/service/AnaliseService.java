package com.cloudsaver.service;

import com.cloudsaver.dto.RecursoAws;
import com.cloudsaver.dto.ResultadoAnalise;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AnaliseService {

    // Custo estimado por hora (USD) de cada tipo de instância EC2.
    private static final Map<String, BigDecimal> PRECO_HORA = Map.of(
        "t2.micro",   new BigDecimal("0.0116"),
        "t3.micro",   new BigDecimal("0.0104"),
        "t3.small",   new BigDecimal("0.0208"),
        "t3.medium",  new BigDecimal("0.0416"),
        "t3.large",   new BigDecimal("0.0832"),
        "m5.large",   new BigDecimal("0.0960"),
        "m5.xlarge",  new BigDecimal("0.1920"),
        "c5.large",   new BigDecimal("0.0850"),
        "r5.large",   new BigDecimal("0.1260")
    );

    private static final BigDecimal HORAS_MES = new BigDecimal("730");
    private static final BigDecimal PRECO_PADRAO = new BigDecimal("0.0500");
    private static final BigDecimal EBS_GB_MES = new BigDecimal("0.08");
    private static final BigDecimal SNAPSHOT_GB_MES = new BigDecimal("0.05");
    private static final BigDecimal IPV4_HORA = new BigDecimal("0.005");
    private static final BigDecimal NAT_GATEWAY_HORA = new BigDecimal("0.045");
    private static final BigDecimal LOAD_BALANCER_HORA = new BigDecimal("0.0225");

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public ResultadoAnalise analisar(MultipartFile arquivo) {
        List<RecursoAws> recursos = lerRecursos(arquivo);
        List<ResultadoAnalise.RecursoResumo> resumos = new ArrayList<>();
        List<ResultadoAnalise.InstanciaResumo> instanciasLegadas = new ArrayList<>();
        BigDecimal custoTotal = BigDecimal.ZERO;
        int totalParadas = 0;

        for (RecursoAws recurso : recursos) {
            BigDecimal custo = custoMensal(recurso);
            custoTotal = custoTotal.add(custo);
            resumos.add(ResultadoAnalise.RecursoResumo.builder()
                    .id(recurso.id())
                    .servico(recurso.servico())
                    .tipo(recurso.tipo())
                    .estado(recurso.estado())
                    .custoMensal(custo)
                    .ocioso(recurso.ocioso())
                    .build());
            if (recurso.ocioso()) {
                totalParadas++;
            }
            if ("EC2".equals(recurso.servico())) {
                instanciasLegadas.add(ResultadoAnalise.InstanciaResumo.builder()
                        .id(recurso.id()).tipo(recurso.tipo()).custoMensal(custo).build());
            }
        }

        String recomendacao = gerarRecomendacao(recursos, custoTotal);

        return ResultadoAnalise.builder()
                .totalRecursos(recursos.size())
                .totalParadas(totalParadas)
                .custoMensal(custoTotal)
                .recomendacao(recomendacao)
                .recursos(resumos)
                .instancias(instanciasLegadas)
                .build();
    }

    // --- Helpers ---

    private List<RecursoAws> lerRecursos(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("O arquivo está vazio.");
        }
        try {
            JsonNode raiz = objectMapper.readTree(arquivo.getInputStream());
            List<RecursoAws> recursos = new ArrayList<>();
            if (raiz.isArray()) {
                // Formato antigo: a aplicação analisava somente EC2 paradas.
                adicionarInstanciasLegadas(recursos, raiz);
            } else if (raiz.isObject()) {
                adicionarColecao(recursos, raiz, "ec2Instances", "EC2", "InstanceId");
                adicionarColecao(recursos, raiz, "ebsVolumes", "EBS", "VolumeId");
                adicionarColecao(recursos, raiz, "ebsSnapshots", "EBS Snapshot", "SnapshotId");
                adicionarColecao(recursos, raiz, "elasticIps", "Elastic IP", "AllocationId");
                adicionarColecao(recursos, raiz, "natGateways", "NAT Gateway", "NatGatewayId");
                adicionarColecao(recursos, raiz, "loadBalancers", "Load Balancer", "LoadBalancerArn");
                adicionarColecao(recursos, raiz, "rdsInstances", "RDS", "DBInstanceIdentifier");
                adicionarColecao(recursos, raiz, "s3Buckets", "S3", "Name");
                adicionarColecao(recursos, raiz, "resources", "AWS", "Id");
            }
            return recursos;
        } catch (Exception e) {
            throw new IllegalArgumentException("Falha ao ler o JSON: " + e.getMessage(), e);
        }
    }

    private void adicionarInstanciasLegadas(List<RecursoAws> recursos, JsonNode no) {
        if (no.isArray()) {
            no.forEach(item -> adicionarInstanciasLegadas(recursos, item));
            return;
        }
        if (!no.isObject() || !"stopped".equalsIgnoreCase(texto(no, "State", ""))) {
            return;
        }
        adicionarRecurso(recursos, no, "EC2", "InstanceId");
        RecursoAws ultimo = recursos.remove(recursos.size() - 1);
        recursos.add(new RecursoAws(ultimo.id(), ultimo.servico(), ultimo.tipo(),
                ultimo.estado(), custoMensal(ultimo.tipo()), true, ultimo.dados()));
    }

    private void adicionarColecao(List<RecursoAws> recursos, JsonNode raiz,
                                  String campo, String servico, String campoId) {
        JsonNode colecao = raiz.get(campo);
        if (colecao == null) {
            return;
        }
        adicionarItensColecao(recursos, colecao, servico, campoId);
    }

    private void adicionarItensColecao(List<RecursoAws> recursos, JsonNode colecao,
                                       String servico, String campoId) {
        if (colecao.isArray()) {
            colecao.forEach(item -> adicionarItensColecao(recursos, item, servico, campoId));
            return;
        }
        if (!colecao.isObject()) {
            return;
        }
        JsonNode valor = colecao.get("value");
        if (valor != null) {
            adicionarItensColecao(recursos, valor, servico, campoId);
            return;
        }
        adicionarRecurso(recursos, colecao, servico, campoId);
    }

    private void adicionarRecurso(List<RecursoAws> recursos, JsonNode item,
                                  String servico, String campoId) {
        if (item == null || !item.isObject()) {
            return;
        }
        String id = texto(item, campoId, texto(item, "id", "recurso-sem-id"));
        String tipo = texto(item, "InstanceType", texto(item, "instanceType",
                texto(item, "VolumeType", texto(item, "type", servico))));
        String estado = texto(item, "State", texto(item, "state",
                texto(item, "StateName", "unknown")));
        boolean ocioso = "stopped".equalsIgnoreCase(estado)
                || "available".equalsIgnoreCase(estado)
                || "unassociated".equalsIgnoreCase(estado)
                || "unused".equalsIgnoreCase(estado)
                || ("Elastic IP".equals(servico) && !item.hasNonNull("AssociationId"));
        recursos.add(new RecursoAws(id, servico, tipo, estado,
                valorDecimal(item, "monthlyCost", valorDecimal(item, "custoMensal",
                        valorDecimal(item, "costMonthly", valorDecimal(item, "MonthlyCost",
                                valorDecimal(item, "cost", null))))),
                ocioso, item));
    }

    private String texto(JsonNode node, String campo, String padrao) {
        JsonNode valor = node.get(campo);
        if (valor == null || valor.isNull()) {
            return padrao;
        }
        if (valor.isObject() && valor.has("Name")) {
            return valor.get("Name").asText(padrao);
        }
        return valor.asText(padrao);
    }

    private BigDecimal valorDecimal(JsonNode node, String campo, BigDecimal padrao) {
        JsonNode valor = node.get(campo);
        if (valor == null || !valor.isNumber() && !valor.isTextual()) {
            return padrao;
        }
        try {
            return new BigDecimal(valor.asText());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private BigDecimal custoMensal(RecursoAws recurso) {
        if (recurso.custoMensal() != null) {
            return recurso.custoMensal().setScale(2, RoundingMode.HALF_UP);
        }
        JsonNode dados = recurso.dados();
        return switch (recurso.servico()) {
            case "EC2" -> "running".equalsIgnoreCase(recurso.estado())
                    ? custoMensal(recurso.tipo()) : BigDecimal.ZERO;
            case "EBS" -> valorDecimal(dados, "Size", BigDecimal.ZERO)
                    .multiply(EBS_GB_MES).setScale(2, RoundingMode.HALF_UP);
            case "EBS Snapshot" -> valorDecimal(dados, "VolumeSize", valorDecimal(dados, "Size", BigDecimal.ZERO))
                    .multiply(SNAPSHOT_GB_MES).setScale(2, RoundingMode.HALF_UP);
            case "Elastic IP" -> custoPorHora(IPV4_HORA);
            case "NAT Gateway" -> custoPorHora(NAT_GATEWAY_HORA);
            case "Load Balancer" -> custoPorHora(LOAD_BALANCER_HORA);
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal custoPorHora(BigDecimal valor) {
        return valor.multiply(HORAS_MES).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal custoMensal(String tipo) {
        BigDecimal precoHora = (tipo != null)
                ? PRECO_HORA.getOrDefault(tipo.toLowerCase(Locale.ROOT), PRECO_PADRAO)
                : PRECO_PADRAO;
        return precoHora.multiply(HORAS_MES).setScale(2, RoundingMode.HALF_UP);
    }

    private String gerarRecomendacao(List<RecursoAws> recursos, BigDecimal custoTotal) {
        long ociosos = recursos.stream().filter(RecursoAws::ocioso).count();
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return "IA indisponível (chave não configurada). " + recursos.size()
                    + " recurso(s) analisado(s), sendo " + ociosos
                    + " potencialmente ocioso(s), com custo estimado de ~US$ " + custoTotal + "/mês.";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            String prompt = montarPrompt(recursos, custoTotal);
            Map<String, Object> body = Map.of(
                    "model", groqModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", "Você é especialista em FinOps e AWS. Responda em português, de forma direta."),
                            Map.of("role", "user", "content", prompt)));
            Map<?, ?> response = restTemplate.postForObject(groqApiUrl,
                    new HttpEntity<>(body, headers), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            return "Falha ao chamar a Groq AI: " + e.getMessage();
        }
    }

    private String montarPrompt(List<RecursoAws> recursos, BigDecimal custoTotal) {
        StringBuilder sb = new StringBuilder("Inventário AWS analisado:\n");
        sb.append("- Recursos: ").append(recursos.size())
                .append("\n- Custo mensal estimado: US$ ").append(custoTotal).append("\n");
        recursos.forEach(r -> sb.append("  * ").append(r.servico()).append(" ")
                .append(r.id()).append(" (").append(r.tipo()).append(", ")
                .append(r.estado()).append("): US$ ").append(custoMensal(r)).append("\n"));
        sb.append("""

                Forneça a resposta em português, usando exatamente estas seções:
                1. Avaliação do impacto financeiro
                2. Recomendação prática
                3. Prioridade de ação

                Na seção "Prioridade de ação", responda com uma tabela Markdown.
                """);
        return sb.toString();
    }
}
