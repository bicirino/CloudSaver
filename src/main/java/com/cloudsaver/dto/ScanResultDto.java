package main.java.com.cloudsaver.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/* 
    Esse arquivo é responsável por formatar os dados da análise para serem devolvidos na resposta da API REST e exibidos no painel 

    Ele contém informações resumidas da varredura, como o total de recursos ociosos encontrados, o valor estimado de desperdício e a recomendação da IA sobre o que fazer com os recursos ociosos

*/


@Getter
@Builder
public class ScanResultDto {
    private Long reportId;
    private LocalDateTime scannedAt;
    private Integer totalResourcesFound;
    private BigDecimal totalMonthlyWasteUsd;
    private String aiRecommendation;
    private List<IdleResourceDto> idleResources;

    @Getter
    @Builder
    public static class IdleResourceDto {
        private String resourceId;
        private String resourceType;
        private String region;
        private BigDecimal monthlyCostUsd;
        private String details;
    }
}