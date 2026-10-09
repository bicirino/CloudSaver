package com.cloudsaver.dto; 

import lombok.AllArgsConstructor; 
import lombok.Data; 
import lombok.Builder; 
import lombok.NoArgsConstructor; 

import java.time.LocalDateTime; 
import java.util.List; 

/* 

DTO de resposta para o dashboard e para a API REST 

Reúne o resumo da varredura e a lista detalhada de recursos ociosos mapeados 

*/ 

@Data
@Builder 
@AllArgsConstructor 
@NoArgsConstructor 
public class ScanReportDTO { 

    // Primary Key (ID) do relatório de varredura  
    private Long id; 

    // Data e hora da varredura 
    private LocalDateTime scannedAt; 

    // Status da varredura (IN_PROGRESS, COMPLETED, FAILED)
    private String status; 

    // Total de recursos ociosos encontrados na varredura 
    private Integer totalIdleResources; 

    // Estimativa do valor total de desperdício em USD 
    private Double totalEstimatedWasteUsd; 

    // Recomendação da IA sobre o que fazer com os recursos ociosos 
    private String aiRecommendation;  

    // Lista detalhada de recursos ociosos encontrados na varredura  
    private List<IdleResourceDTO> idleResources;  

    @Data 
    @Builder 
    @AllArgsConstructor 
    @NoArgsConstructor 
    public static class IdleResourceDTO{ 

        // Id do recurso ocioso encontrado 
        private Long id; 

        // Id do recurso na AWS (ex: "i-0123456789abcdef0" para EC2 ou "vol-0987654321" para EBS)
        private String resourceId; 

        // Tipo do recurso ocioso (ex: "EC2", "EBS", "RDS", "S3", "Lambda", "etc.")
        private String resourceType; 

        // Região onde o recurso está alocado 
        private String region; 

        // Custo mensal estimado de desperdício em dólares 
        private Double estimatedMonthlyCostUsd; 

        // Razão pela qual foi considerado ocioso 
        private String idleReason; 

        // Status do recurso ("IDLE", "TERMINATED", "PAUSED")
        private String status; 
    }

}