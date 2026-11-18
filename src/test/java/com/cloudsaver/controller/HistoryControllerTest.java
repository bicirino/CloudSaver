package com.cloudsaver.controller;

import com.cloudsaver.dto.ResultadoAnalise;
import com.cloudsaver.dto.ScanDto;
import com.cloudsaver.dto.ScanStatsDto;
import com.cloudsaver.security.SupabaseAuthenticationToken;
import com.cloudsaver.security.SupabaseUser;
import com.cloudsaver.service.ScanPersistenceService;
import com.cloudsaver.service.SupabaseAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class HistoryControllerTest {

    private ScanPersistenceService persistenceService;
    private SupabaseAuthService authService;
    private HistoryController controller;

    @BeforeEach
    void setUp() {
        persistenceService = Mockito.mock(ScanPersistenceService.class);
        authService = Mockito.mock(SupabaseAuthService.class);
        controller = new HistoryController(persistenceService, authService);

        SupabaseUser user = SupabaseUser.builder()
                .id("test-user-id")
                .email("test@cloudsaver.com")
                .role("authenticated")
                .build();
        SupabaseAuthenticationToken token = new SupabaseAuthenticationToken(user, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveListarHistoricoDoUsuarioAutenticado() {
        UUID scanId = UUID.randomUUID();
        ScanDto dto = ScanDto.builder()
                .id(scanId)
                .userId("test-user-id")
                .createdAt(Instant.now())
                .custoMensal(new BigDecimal("120.50"))
                .totalRecursos(5)
                .totalParadas(2)
                .nomeArquivo("resources.json")
                .recomendacao("Otimize seus recursos")
                .build();

        when(persistenceService.listarPorUsuario("test-user-id")).thenReturn(List.of(dto));

        ResponseEntity<List<ScanDto>> response = controller.listarHistorico();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getId()).isEqualTo(scanId);
    }

    @Test
    void deveRetornarEstatisticasDoUsuario() {
        ScanStatsDto stats = ScanStatsDto.builder()
                .totalScans(3)
                .custoAtual(new BigDecimal("95.00"))
                .custoAnterior(new BigDecimal("120.00"))
                .variacaoPercentual(new BigDecimal("-20.83"))
                .variacaoAbsoluta(new BigDecimal("-25.00"))
                .totalRecursosAtual(4)
                .totalParadasAtual(1)
                .build();

        when(persistenceService.calcularEstatisticas("test-user-id")).thenReturn(stats);

        ResponseEntity<ScanStatsDto> response = controller.obterEstatisticas();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTotalScans()).isEqualTo(3);
        assertThat(response.getBody().getVariacaoPercentual()).isEqualTo(new BigDecimal("-20.83"));
    }

    @Test
    void deveRetornarDetalheDoScan() {
        UUID scanId = UUID.randomUUID();
        ResultadoAnalise resultado = ResultadoAnalise.builder()
                .scanId(scanId.toString())
                .totalRecursos(10)
                .custoMensal(new BigDecimal("350.00"))
                .recomendacao("Parar EC2 ociosa")
                .build();

        when(persistenceService.buscarDetalhePorIdEUsuario(eq(scanId), eq("test-user-id")))
                .thenReturn(Optional.of(resultado));

        ResponseEntity<ResultadoAnalise> response = controller.obterDetalheScan(scanId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCustoMensal()).isEqualTo(new BigDecimal("350.00"));
    }

    @Test
    void deveLancar404QuandoScanNaoEncontrado() {
        UUID scanId = UUID.randomUUID();
        when(persistenceService.buscarDetalhePorIdEUsuario(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.obterDetalheScan(scanId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
