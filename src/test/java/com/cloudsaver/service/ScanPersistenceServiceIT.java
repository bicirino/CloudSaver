package com.cloudsaver.service;

import com.cloudsaver.dto.ResultadoAnalise;
import com.cloudsaver.dto.ScanDto;
import com.cloudsaver.dto.ScanStatsDto;
import com.cloudsaver.repository.ScanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ScanPersistenceServiceIT {

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private ScanPersistenceService service;

    @Test
    void deveSalvarEListarScansDoUsuario() {
        String userId = "user-abc-123";

        ResultadoAnalise r1 = ResultadoAnalise.builder()
                .totalRecursos(8)
                .totalParadas(2)
                .custoMensal(new BigDecimal("150.00"))
                .recomendacao("Primeira recomendação")
                .build();

        ResultadoAnalise salvo1 = service.salvarScan(r1, userId, "inventory-1.json");
        assertThat(salvo1.getScanId()).isNotNull();

        ResultadoAnalise r2 = ResultadoAnalise.builder()
                .totalRecursos(7)
                .totalParadas(1)
                .custoMensal(new BigDecimal("120.00"))
                .recomendacao("Segunda recomendação")
                .build();

        ResultadoAnalise salvo2 = service.salvarScan(r2, userId, "inventory-2.json");
        assertThat(salvo2.getScanId()).isNotNull();

        List<ScanDto> lista = service.listarPorUsuario(userId);
        assertThat(lista).hasSize(2);
        // O mais recente deve vir primeiro
        assertThat(lista.get(0).getId().toString()).isEqualTo(salvo2.getScanId());
        assertThat(lista.get(1).getId().toString()).isEqualTo(salvo1.getScanId());

        // Testar busca detalhada
        Optional<ResultadoAnalise> detalhe = service.buscarDetalhePorIdEUsuario(UUID.fromString(salvo2.getScanId()), userId);
        assertThat(detalhe).isPresent();
        assertThat(detalhe.get().getCustoMensal()).isEqualTo(new BigDecimal("120.00"));
        assertThat(detalhe.get().getRecomendacao()).isEqualTo("Segunda recomendação");

        // Testar cálculo de evolução/estatísticas
        ScanStatsDto stats = service.calcularEstatisticas(userId);
        assertThat(stats.getTotalScans()).isEqualTo(2);
        assertThat(stats.getCustoAtual()).isEqualTo(new BigDecimal("120.00"));
        assertThat(stats.getCustoAnterior()).isEqualTo(new BigDecimal("150.00"));
        assertThat(stats.getVariacaoAbsoluta()).isEqualTo(new BigDecimal("-30.00"));
        // (-30 / 150) * 100 = -20%
        assertThat(stats.getVariacaoPercentual()).isEqualTo(new BigDecimal("-20.00"));
    }
}
