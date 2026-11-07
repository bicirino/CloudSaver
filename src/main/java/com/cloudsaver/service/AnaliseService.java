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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AnaliseService {

    // Custo estimado por hora (USD) de cada tipo de instância EC2.
    private static final Map<String, BigDecimal> PRECO_HORA = Map.ofEntries(
        Map.entry("t2.micro", new BigDecimal("0.0116")),
        Map.entry("t2.small", new BigDecimal("0.0230")),
        Map.entry("t2.medium", new BigDecimal("0.0464")),
        Map.entry("t2.large", new BigDecimal("0.0928")),
        Map.entry("t3.micro", new BigDecimal("0.0104")),
        Map.entry("t3.small", new BigDecimal("0.0208")),
        Map.entry("t3.medium", new BigDecimal("0.0416")),
        Map.entry("t3.large", new BigDecimal("0.0832")),
        Map.entry("t3.xlarge", new BigDecimal("0.1664")),
        Map.entry("t3.2xlarge", new BigDecimal("0.3328")),
        Map.entry("t4g.micro", new BigDecimal("0.0090")),
        Map.entry("t4g.small", new BigDecimal("0.0180")),
        Map.entry("t4g.medium", new BigDecimal("0.0360")),
        Map.entry("t4g.large", new BigDecimal("0.0720")),
        Map.entry("m5.large", new BigDecimal("0.0960")),
        Map.entry("m5.xlarge", new BigDecimal("0.1920")),
        Map.entry("m5.2xlarge", new BigDecimal("0.3840")),
        Map.entry("m6g.large", new BigDecimal("0.1080")),
        Map.entry("m6g.xlarge", new BigDecimal("0.2160")),
        Map.entry("m6i.large", new BigDecimal("0.1150")),
        Map.entry("m6i.xlarge", new BigDecimal("0.2300")),
        Map.entry("m7g.large", new BigDecimal("0.1160")),
        Map.entry("m7g.xlarge", new BigDecimal("0.2320")),
        Map.entry("c5.large", new BigDecimal("0.0850")),
        Map.entry("c5.xlarge", new BigDecimal("0.1700")),
        Map.entry("c6g.large", new BigDecimal("0.0900")),
        Map.entry("c6i.large", new BigDecimal("0.1020")),
        Map.entry("r5.large", new BigDecimal("0.1260")),
        Map.entry("r5.xlarge", new BigDecimal("0.2520")),
        Map.entry("r6g.large", new BigDecimal("0.1440")),
        Map.entry("r6i.large", new BigDecimal("0.1570"))
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
                adicionarInventarioPadronizado(recursos, raiz.get("resources"));
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

    private void adicionarInventarioPadronizado(List<RecursoAws> recursos, JsonNode colecao) {
        if (colecao == null || colecao.isNull()) {
            return;
        }
        adicionarItensColecao(recursos, colecao, "AWS", "Id");
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
        String id = texto(item, campoId, texto(item, "id",
                texto(item, "resourceId", texto(item, "resource_id",
                        texto(item, "arn", texto(item, "name", "recurso-sem-id"))))));
        String servicoRecurso = servico;
        if ("AWS".equals(servico)) {
            servicoRecurso = texto(item, "resourceType", texto(item, "resource_type", servico));
        }
        String tipo = textoQualquer(item,
                "InstanceType", "instanceType", "instance_type",
                "DBInstanceClass", "dbInstanceClass",
                "VolumeType", "volume_type", "volumeType",
                "Type", "type", "ResourceType", "resourceType",
                "resource_type", "StorageType", "storage_type",
                "Family", "family");
        if (tipo == null || tipo.isBlank()) {
            tipo = servicoRecurso;
        }
        String estado = texto(item, "State", texto(item, "state",
                texto(item, "StateName", texto(item, "status", "unknown"))));
        boolean ocioso = "stopped".equalsIgnoreCase(estado)
                || "available".equalsIgnoreCase(estado)
                || "unassociated".equalsIgnoreCase(estado)
                || "unused".equalsIgnoreCase(estado)
                || ("Elastic IP".equals(servico) && !item.hasNonNull("AssociationId"));
        recursos.add(new RecursoAws(id, servicoRecurso, tipo, estado,
                custoInformado(item),
                ocioso, item));
    }

    private BigDecimal custoInformado(JsonNode item) {
        BigDecimal padrao = null;
        String[] campos = {
                "monthlyCost", "monthly_cost", "custoMensal", "costMonthly",
                "MonthlyCost", "monthlyCostUsd", "monthlyCostUSD", "monthly_cost_usd",
                "costUsd", "costUSD", "estimatedMonthlyCost", "monthly", "cost"
        };
        for (String campo : campos) {
            BigDecimal valor = valorDecimal(item, campo, padrao);
            if (valor != null) {
                return valor;
            }
        }
        return null;
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

    private String textoQualquer(JsonNode node, String... campos) {
        for (String campo : campos) {
            JsonNode valor = node.get(campo);
            if (valor != null && !valor.isNull()) {
                if (valor.isObject() && valor.has("Name")) {
                    return valor.get("Name").asText();
                }
                return valor.asText();
            }
        }
        return null;
    }

    private BigDecimal valorDecimal(JsonNode node, String campo, BigDecimal padrao) {
        JsonNode valor = procurarCampo(node, campo);
        if (valor == null) {
            return padrao;
        }
        valor = resolverValorMonetario(valor);
        if (valor == null || (!valor.isNumber() && !valor.isTextual())) {
            return padrao;
        }
        try {
            String texto = valor.asText().trim();
            if (texto.isBlank()) {
                return padrao;
            }
            texto = texto.replaceAll("(?i)(?:usd|us\\$|\\$)", "")
                    .replace(" ", "")
                    .replace("(", "-")
                    .replace(")", "");
            if (texto.contains(",") && texto.contains(".")) {
                texto = texto.replace(",", "");
            } else if (texto.contains(",")) {
                texto = texto.replace(',', '.');
            }
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private JsonNode procurarCampo(JsonNode node, String campo) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode valor = node.get(campo);
            if (valor != null) {
                return valor;
            }
            Iterator<Map.Entry<String, JsonNode>> campos = node.fields();
            while (campos.hasNext()) {
                Map.Entry<String, JsonNode> entrada = campos.next();
                JsonNode encontrado = procurarCampo(entrada.getValue(), campo);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode encontrado = procurarCampo(item, campo);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    private JsonNode resolverValorMonetario(JsonNode valor) {
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (valor.isObject()) {
            JsonNode[] camposPrioritarios = {
                    valor.get("value"), valor.get("amount"), valor.get("monthly"),
                    valor.get("monthlyCost"), valor.get("usd"), valor.get("total"),
                    valor.get("price"), valor.get("cost")
            };
            for (JsonNode candidato : camposPrioritarios) {
                JsonNode resolvido = resolverValorMonetario(candidato);
                if (resolvido != null) {
                    return resolvido;
                }
            }
            return null;
        }
        if (valor.isTextual() || valor.isNumber()) {
            return valor;
        }
        return null;
    }

    private BigDecimal custoMensal(RecursoAws recurso) {
        if (recurso.custoMensal() != null) {
            return recurso.custoMensal().setScale(2, RoundingMode.HALF_UP);
        }
        JsonNode dados = recurso.dados();
        return switch (recurso.servico()) {
            case "EC2" -> "running".equalsIgnoreCase(recurso.estado())
                    ? custoMensal(recurso.tipo()) : BigDecimal.ZERO;
            case "EBS" -> valorDecimal(dados, "Size", valorDecimal(dados, "size_gb", BigDecimal.ZERO))
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
        if (tipo == null || tipo.isBlank()) {
            return PRECO_PADRAO.multiply(HORAS_MES).setScale(2, RoundingMode.HALF_UP);
        }
        String tipoNormalizado = tipo.toLowerCase(Locale.ROOT).trim();
        BigDecimal precoHora = PRECO_HORA.get(tipoNormalizado);
        if (precoHora == null) {
            String base = tipoNormalizado.replace("db.", "").replace("*_", "");
            for (Map.Entry<String, BigDecimal> entrada : PRECO_HORA.entrySet()) {
                if (base.startsWith(entrada.getKey()) || tipoNormalizado.startsWith(entrada.getKey())) {
                    precoHora = entrada.getValue();
                    break;
                }
            }
        }
        if (precoHora == null) {
            precoHora = PRECO_PADRAO;
        }
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
