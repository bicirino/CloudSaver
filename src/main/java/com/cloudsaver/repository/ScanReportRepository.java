package com.cloudsaver.repository; 

import com.cloudsaver.model.ScanReport; 
import org.springframework.data.jpa.repository.JpaRepository; 
import org.springframework.stereotype.Repository;  

// "Optional" é um tipo de dado que representa um valor que pode ou não existir. 
import java.util.Optional;  


// "Repository" serve para indicar que a classe é um repositório de dados (que é um tipo de classe responsável pelo CRUD de um banco de dados) 
@Repository 
public interface ScanReportRepository extends JpaRepository<ScanReport, Long>{ 

    // Retorna o relatório de varredura mais recente gerado pelo sistema 
    Optional<ScanReport> findTopByOrderByScannedAtDesc(); 
}