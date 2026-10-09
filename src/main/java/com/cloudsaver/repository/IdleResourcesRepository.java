package com.cloudsaver.repository;  

import com.cloudsaver.model.IdleResource; 
import org.springframework.data.jpa.repository.JpaRepository; 
import org.springframework.stereotype.Repository;  

import java.util.List;  

@Repository 
public interface IdleResourcesRepository extends JpaRepository<IdleResource, Long>{ 


    // Retorna todos os recursos ociosos associados a um relatório de varredura específico 
    List<IdleResource> findByScanReportId(Long scanReportId); 

    // Retorna todos os recursos ociosos associados a um tipo de recurso e um status específico 
    List<IdleResource> findByResourceTypeAndStatus(String resourceType, String status); 


} 