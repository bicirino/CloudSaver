package com.cloudsaver.repository;

import com.cloudsaver.entity.ScanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanRepository extends JpaRepository<ScanEntity, UUID> {

    List<ScanEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<ScanEntity> findByIdAndUserId(UUID id, String userId);

    void deleteByIdAndUserId(UUID id, String userId);
}
