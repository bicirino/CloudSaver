package com.cloudsaver.model;  

// Importações para a persistência JPA  
import jakarta.persistence.*; 
import lombok.*; 

// Importações para os tipos de dados  
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity                                                                  // Entidade para armazenar o resumo de cada varredura 
@Table(name = "scan_reports")                                           // Mapeia para a tabela scan_reports 
@Getter                                                                // Lombok - Gera getters para todos os campos 
@Setter                                                               // Lombok - Gera setters para todos os campos 
@NoArgsConstructor                                                   // Lombok - Construtor sem argumentos 
@AllArgsConstructor                                                 // Lombok - Construtor com todos os argumentos  
@Builder                                                           // Lombok - Gera um construtor com todos os campos 


public class ScanReport { 


    @Id                                                                  // Chave primária 
    @GeneratedValue(strategy = GenerationType.IDENTITY)                 // Auto-incremento 
    private Long id;

    @Column(name = "scanned_at", nullable = false)                     // Data da varredura 
    private LocalDateTime scannedAt;

    @Column(name = "total_resources_found", nullable = false)          // Total de recursos ociosos encontrados 
    private Integer totalResourcesFound;

    @Column(name = "total_monthly_waste_usd", nullable = false, precision = 10, scale = 2) // Total de gastos mensais com recursos ociosos 
    private BigDecimal totalMonthlyWasteUsd;

    @Column(name = "ai_recommendation", columnDefinition = "TEXT") // Recomendação da IA 
    private String aiRecommendation;

    @OneToMany(mappedBy = "scanReport", cascade = CascadeType.ALL, orphanRemoval = true) // Relação com os recursos ociosos 
    @Builder.Default
    private List<IdleResource> idleResources = new ArrayList<>();                         // Total de gastos mensais com recursos ociosos 
}