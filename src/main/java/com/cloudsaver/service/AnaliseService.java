package com.cloudsaver.service;

import com.cloudsaver.dto.InstanciaEc2;
import com.cloudsaver.dto.ResultadoAnalise;
import com.fasterxml.jackson.core.type.TypeReference;
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

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public ResultadoAnalise analisar(MultipartFile arquivo) {
        List<InstanciaEc2> instancias = lerInstancias(arquivo);

        List<InstanciaEc2> paradas = instancias.stream()
                .filter(InstanciaEc2::isParada)
                .toList();

        List<ResultadoAnalise.InstanciaResumo> resumos = new ArrayList<>();
        BigDecimal custoTotal = BigDecimal.ZERO;

        for (InstanciaEc2 inst : paradas) {
            BigDecimal custo = custoMensal(inst.getInstanceType());
            custoTotal = custoTotal.add(custo);
            resumos.add(ResultadoAnalise.InstanciaResumo.builder()
                    .id(inst.getInstanceId())
                    .tipo(inst.getInstanceType())
                    .custoMensal(custo)
                    .build());
        }

        String recomendacao = gerarRecomendacao(paradas, custoTotal);

        return ResultadoAnalise.builder()
                .totalParadas(paradas.size())
                .custoMensal(custoTotal)
                .recomendacao(recomendacao)
                .instancias(resumos)
                .build();
    }

    // --- Helpers ---

    private List<InstanciaEc2> lerInstancias(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("O arquivo está vazio.");
        }
        try {
            // O --query do AWS CLI pode retornar lista de listas; achatamos tudo.
            Object raiz = objectMapper.readValue(arquivo.getInputStream(), Object.class);
            return achatar(raiz);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao ler o JSON: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<InstanciaEc2> achatar(Object raiz) {
        List<InstanciaEc2> resultado = new ArrayList<>();
        if (raiz instanceof List<?> lista) {
            for (Object item : lista) {
                if (item instanceof List<?>) {
                    resultado.addAll(achatar(item));
                } else if (item instanceof Map<?, ?>) {
                    resultado.add(objectMapper.convertValue(item, InstanciaEc2.class));
                }
            }
        }
        return resultado;
    }

    private BigDecimal custoMensal(String tipo) {
        BigDecimal precoHora = (tipo != null)
                ? PRECO_HORA.getOrDefault(tipo.toLowerCase(), PRECO_PADRAO)
                : PRECO_PADRAO;
        return precoHora.multiply(HORAS_MES).setScale(2, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    private String gerarRecomendacao(List<InstanciaEc2> paradas, BigDecimal custoTotal) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return "IA indisponível (chave não configurada). "
                    + paradas.size() + " instância(s) parada(s) gerando ~US$ " + custoTotal + "/mês.";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            String prompt = montarPrompt(paradas, custoTotal);

            Map<String, Object> body = Map.of(
                "model", groqModel,
                "messages", List.of(
                    Map.of("role", "system",
                           "content", "Você é especialista em FinOps e AWS. Responda em português, de forma direta."),
                    Map.of("role", "user", "content", prompt)
                )
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            Map<?, ?> response = restTemplate.postForObject(groqApiUrl, request, Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (Exception e) {
            return "Falha ao chamar a Groq AI: " + e.getMessage();
        }
    }

    private String montarPrompt(List<InstanciaEc2> paradas, BigDecimal custoTotal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Recursos ociosos na AWS:\n")
          .append("- Instâncias EC2 paradas: ").append(paradas.size()).append("\n")
          .append("- Custo mensal estimado: US$ ").append(custoTotal).append("\n\n")
          .append("Instâncias:\n");
        for (InstanciaEc2 i : paradas) {
            sb.append("  * ").append(i.getInstanceId()).append(" (").append(i.getInstanceType()).append(")\n");
        }
        sb.append("""

                Forneça a resposta em português, usando exatamente estas seções:
                1. Avaliação do impacto financeiro
                2. Recomendação prática
                3. Prioridade de ação

                Na seção "Prioridade de ação", responda obrigatoriamente com uma tabela Markdown,
                sem lista, neste formato exato:
                | Ação | Prioridade | Por quê |
                |---|---|---|
                | descrição da ação | Alta, Média ou Baixa | justificativa |

                Não altere os nomes das colunas e não coloque a prioridade fora da tabela.
                """);
        return sb.toString();
    }
}
