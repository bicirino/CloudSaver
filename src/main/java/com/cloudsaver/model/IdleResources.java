package com.cloudsaver.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "idle_resources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class IdleResource {

    @Id                                                                  // Chave primária 
    @GeneratedValue(strategy = GenerationType.IDENTITY)                 // Auto-incremento 
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)                                  // Relação com o report de varredura 
    @JoinColumn(name = "scan_report_id", nullable = false)             // Chave estrangeira para o report de varredura 
    private ScanReport scanReport;

    @Column(name = "resource_id", nullable = false, length = 100)        // ID do recurso 
    private String resourceId;

    @Column(name = "resource_type", nullable = false, length = 50)        // Tipo do recurso 
    private String resourceType;

    @Column(name = "region", nullable = false, length = 30)                // Região do recurso 
    private String region;

    @Column(name = "monthly_cost_usd", nullable = false, precision = 10, scale = 2) // Custo mensal do recurso 
    private BigDecimal monthlyCostUsd;

    @Column(name = "details", columnDefinition = "TEXT")                     // Detalhes do recurso 
    private String details;
}