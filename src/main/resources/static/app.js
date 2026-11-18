// Cria uma função assíncrona para copiar o comando de código 
async function copyCode(button) {
    const container = button.closest('.comando');
    const codeElement = container?.querySelector('code');

    if (!codeElement) {
        console.error('Não foi possível localizar o comando para copiar.');
        return;
    }

    try {
        await navigator.clipboard.writeText(codeElement.innerText);
        container.classList.remove('is-copiado');
        void container.offsetWidth;
        container.classList.add('is-copiado');
    } catch (error) {
        console.error('Erro ao copiar:', error);
        button.setAttribute('aria-label', 'Erro ao copiar comando');
        button.setAttribute('title', 'Erro ao copiar comando');
    }
}

// Função para formatar o valor em USD 
function formatarUsd(valor) {
    const numero = Number(valor);
    if (Number.isNaN(numero)) {
        return '—';
    }
    return new Intl.NumberFormat('pt-BR', {
        style: 'currency',
        currency: 'USD',
    }).format(numero);
}

// Função para formatar data e hora ISO
function formatarDataHora(isoString) {
    if (!isoString) return '—';
    try {
        const data = new Date(isoString);
        return new Intl.DateTimeFormat('pt-BR', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        }).format(data);
    } catch {
        return isoString;
    }
}

let graficoCustosServico;
let graficoCustosRecurso;
let graficoTendenciaCustos;
const LIMITE_RECURSOS_GRAFICO = 12;
const LIMITE_TAMANHO_ARQUIVO_KB = 100;
const LIMITE_TAMANHO_ARQUIVO_BYTES = LIMITE_TAMANHO_ARQUIVO_KB * 1024;

// ==============================================================================
// Autenticação Supabase
// ==============================================================================
let supabaseClient = null;
let currentUser = null;
let authRequired = false;
let modoAuthAtual = 'login';

async function inicializarSupabase() {
    try {
        const resposta = await fetch('/api/config');
        if (resposta.ok) {
            const config = await resposta.json();
            authRequired = config.authRequired === true;

            if (config.supabaseUrl && config.supabaseAnonKey && window.supabase) {
                supabaseClient = window.supabase.createClient(config.supabaseUrl, config.supabaseAnonKey);

                supabaseClient.auth.onAuthStateChange(async (event, session) => {
                    currentUser = session?.user ?? null;
                    atualizarInterfaceAuth();
                    if (window.location.pathname.endsWith('historico.html')) {
                        carregarHistorico();
                    }
                });

                const { data: { session } } = await supabaseClient.auth.getSession();
                currentUser = session?.user ?? null;
            }
        }
    } catch (e) {
        console.warn('Configuração do Supabase não pôde ser carregada:', e);
    }

    atualizarInterfaceAuth();

    if (window.location.pathname.endsWith('historico.html')) {
        carregarHistorico();
    }
}

async function obterTokenAuth() {
    if (supabaseClient) {
        try {
            const { data: { session } } = await supabaseClient.auth.getSession();
            if (session?.access_token) {
                return session.access_token;
            }
        } catch (e) {
            console.error('Erro ao obter token Supabase:', e);
        }
    }
    return '';
}

function atualizarInterfaceAuth() {
    const containers = document.querySelectorAll('.auth-nav-container, #auth-nav-container');
    containers.forEach((container) => {
        if (!container) return;
        if (currentUser) {
            const email = currentUser.email || 'Usuário';
            container.innerHTML = `
                <div class="user-badge">
                    <i class="fi fi-rr-user-check" aria-hidden="true"></i>
                    <span class="user-email-text" title="${escaparHtml(email)}">${escaparHtml(email)}</span>
                    <button type="button" class="btn-logout" onclick="fazerLogout()" title="Sair da conta">
                        <i class="fi fi-rr-sign-out-alt" aria-hidden="true"></i> Sair
                    </button>
                </div>
            `;
        } else {
            container.innerHTML = `
                <button type="button" class="btn-outline-auth" onclick="abrirModalAuth('login')">
                    <i class="fi fi-rr-user" aria-hidden="true"></i> Entrar
                </button>
            `;
        }
    });

    // Atualiza avisos específicos da página de análise
    const avisoAnalise = document.getElementById('analise-auth-notice');
    if (avisoAnalise) {
        avisoAnalise.hidden = !!currentUser;
    }

    // Atualiza visão do histórico
    const historicoAlerta = document.getElementById('historico-auth-alert');
    const historicoConteudo = document.getElementById('historico-conteudo');
    if (historicoAlerta && historicoConteudo) {
        const podeVer = currentUser || !authRequired;
        historicoAlerta.hidden = podeVer;
        historicoConteudo.hidden = !podeVer;
    }
}

function abrirModalAuth(modo = 'login') {
    const modal = document.getElementById('modal-auth');
    if (!modal) return;
    modoAuthAtual = modo;
    alternarAbaAuth(modo);
    mostrarAlertaAuth('');
    modal.hidden = false;
    document.getElementById('auth-email')?.focus();
}

function fecharModalAuth() {
    const modal = document.getElementById('modal-auth');
    if (modal) modal.hidden = true;
    mostrarAlertaAuth('');
}

function alternarAbaAuth(modo) {
    modoAuthAtual = modo;
    const tabLogin = document.getElementById('tab-login');
    const tabCadastro = document.getElementById('tab-cadastro');
    const btnSubmit = document.getElementById('btn-auth-submit');
    const titulo = document.getElementById('auth-modal-titulo');

    if (modo === 'login') {
        tabLogin?.classList.add('is-active');
        tabCadastro?.classList.remove('is-active');
        if (btnSubmit) btnSubmit.textContent = 'Entrar';
        if (titulo) titulo.textContent = 'Acessar CloudSaver';
    } else {
        tabCadastro?.classList.add('is-active');
        tabLogin?.classList.remove('is-active');
        if (btnSubmit) btnSubmit.textContent = 'Criar conta';
        if (titulo) titulo.textContent = 'Criar conta no CloudSaver';
    }
    mostrarAlertaAuth('');
}

function mostrarAlertaAuth(mensagem, tipo = 'erro') {
    const alerta = document.getElementById('auth-alerta');
    if (!alerta) return;
    if (!mensagem) {
        alerta.hidden = true;
        alerta.textContent = '';
        alerta.className = 'auth-alerta';
        return;
    }
    alerta.textContent = mensagem;
    alerta.className = `auth-alerta alerta-${tipo}`;
    alerta.hidden = false;
}

async function processarAuth(event) {
    event.preventDefault();
    if (!supabaseClient) {
        mostrarAlertaAuth('Supabase não configurado no servidor. Configure as variáveis SUPABASE_URL e SUPABASE_ANON_KEY no arquivo .env.');
        return;
    }

    const email = document.getElementById('auth-email')?.value?.trim();
    const password = document.getElementById('auth-senha')?.value;
    const btn = document.getElementById('btn-auth-submit');

    if (!email || !password) {
        mostrarAlertaAuth('Por favor, preencha todos os campos.');
        return;
    }

    if (btn) btn.disabled = true;
    mostrarAlertaAuth('');

    try {
        if (modoAuthAtual === 'login') {
            const { data, error } = await supabaseClient.auth.signInWithPassword({ email, password });
            if (error) {
                mostrarAlertaAuth(error.message === 'Invalid login credentials'
                    ? 'E-mail ou senha incorretos.'
                    : `Erro no login: ${error.message}`);
                return;
            }
            currentUser = data.user;
            fecharModalAuth();
            atualizarInterfaceAuth();
            if (window.location.pathname.endsWith('historico.html')) {
                carregarHistorico();
            }
        } else {
            const { data, error } = await supabaseClient.auth.signUp({ email, password });
            if (error) {
                mostrarAlertaAuth(`Erro no cadastro: ${error.message}`);
                return;
            }
            if (data.session) {
                currentUser = data.user;
                fecharModalAuth();
                atualizarInterfaceAuth();
                if (window.location.pathname.endsWith('historico.html')) {
                    carregarHistorico();
                }
            } else {
                mostrarAlertaAuth('Conta criada com sucesso! Verifique seu e-mail para confirmar o cadastro.', 'sucesso');
            }
        }
    } catch (e) {
        mostrarAlertaAuth(`Ocorreu um erro inesperado: ${e.message}`);
    } finally {
        if (btn) btn.disabled = false;
    }
}

async function loginOAuth(provider) {
    if (!supabaseClient) {
        mostrarAlertaAuth('Supabase não configurado. Defina SUPABASE_URL e SUPABASE_ANON_KEY no .env.');
        return;
    }
    try {
        const { error } = await supabaseClient.auth.signInWithOAuth({
            provider,
            options: {
                redirectTo: window.location.href,
            },
        });
        if (error) {
            mostrarAlertaAuth(`Falha ao conectar com ${provider}: ${error.message}`);
        }
    } catch (e) {
        mostrarAlertaAuth(`Erro ao iniciar OAuth: ${e.message}`);
    }
}

async function fazerLogout() {
    if (supabaseClient) {
        await supabaseClient.auth.signOut();
    }
    currentUser = null;
    atualizarInterfaceAuth();
    if (window.location.pathname.endsWith('historico.html')) {
        carregarHistorico();
    }
}

// ==============================================================================
// Dashboard Temporal e Histórico de Scans
// ==============================================================================
async function carregarHistorico() {
    const loading = document.getElementById('historico-loading');
    const erroEl = document.getElementById('historico-erro');
    if (!document.getElementById('historico-conteudo')) return;

    if (loading) loading.hidden = false;
    if (erroEl) erroEl.hidden = true;

    const token = await obterTokenAuth();
    const headers = {};
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    try {
        const [respHistorico, respStats] = await Promise.all([
            fetch('/api/history', { headers }),
            fetch('/api/history/stats', { headers }),
        ]);

        if (respHistorico.status === 401) {
            atualizarInterfaceAuth();
            return;
        }

        if (!respHistorico.ok) {
            throw new Error(`Falha ao obter histórico (HTTP ${respHistorico.status})`);
        }

        const scans = await respHistorico.json();
        let stats = null;
        if (respStats.ok) {
            stats = await respStats.json();
        }

        renderizarEstatisticas(stats, scans);
        renderizarGraficoTendencia(scans);
        renderizarTabelaHistorico(scans);
    } catch (e) {
        console.error(e);
        if (erroEl) {
            erroEl.textContent = 'Não foi possível carregar o histórico de análises. Verifique se o servidor está ativo.';
            erroEl.hidden = false;
        }
    } finally {
        if (loading) loading.hidden = true;
    }
}

function renderizarEstatisticas(stats, scans) {
    const custoAtualEl = document.getElementById('stat-custo-atual');
    const dataAtualEl = document.getElementById('stat-data-atual');
    const variacaoEl = document.getElementById('stat-variacao');
    const variacaoDescEl = document.getElementById('stat-variacao-desc');
    const recursosEl = document.getElementById('stat-recursos');
    const recursosOciososEl = document.getElementById('stat-recursos-ociosos');
    const totalScansEl = document.getElementById('stat-total-scans');

    if (!stats || !scans || scans.length === 0) {
        if (custoAtualEl) custoAtualEl.textContent = '—';
        if (dataAtualEl) dataAtualEl.textContent = 'Nenhum scan';
        if (variacaoEl) variacaoEl.textContent = '—';
        if (variacaoDescEl) variacaoDescEl.textContent = 'Sem dados anteriores';
        if (recursosEl) recursosEl.textContent = '—';
        if (recursosOciososEl) recursosOciososEl.textContent = '—';
        if (totalScansEl) totalScansEl.textContent = '0';
        return;
    }

    const maisRecente = scans[0];
    if (custoAtualEl) custoAtualEl.textContent = formatarUsd(stats.custoAtual);
    if (dataAtualEl) dataAtualEl.textContent = formatarDataHora(maisRecente.createdAt);
    if (recursosEl) recursosEl.textContent = stats.totalRecursosAtual;
    if (recursosOciososEl) recursosOciososEl.textContent = `${stats.totalParadasAtual} potencialmente ociosos`;
    if (totalScansEl) totalScansEl.textContent = stats.totalScans;

    if (variacaoEl) {
        if (stats.totalScans < 2) {
            variacaoEl.innerHTML = '<span class="badge-variacao badge-variacao--neutro">Scan inicial</span>';
            if (variacaoDescEl) variacaoDescEl.textContent = 'Primeira análise registrada';
        } else {
            const varPct = Number(stats.variacaoPercentual);
            const varAbs = Number(stats.variacaoAbsoluta);
            if (varPct < 0) {
                variacaoEl.innerHTML = `<span class="badge-variacao badge-variacao--economia"><i class="fi fi-rr-arrow-trend-down"></i> ${Math.abs(varPct).toFixed(1)}% (${formatarUsd(Math.abs(varAbs))})</span>`;
                if (variacaoDescEl) variacaoDescEl.textContent = 'Economia identificada';
            } else if (varPct > 0) {
                variacaoEl.innerHTML = `<span class="badge-variacao badge-variacao--aumento"><i class="fi fi-rr-arrow-trend-up"></i> +${varPct.toFixed(1)}% (+${formatarUsd(varAbs)})</span>`;
                if (variacaoDescEl) variacaoDescEl.textContent = 'Aumento de custos';
            } else {
                variacaoEl.innerHTML = '<span class="badge-variacao badge-variacao--neutro">0.0% estável</span>';
                if (variacaoDescEl) variacaoDescEl.textContent = 'Sem alteração de custos';
            }
        }
    }
}

function renderizarGraficoTendencia(scans) {
    const canvas = document.getElementById('grafico-tendencia-custos');
    if (!canvas || typeof Chart === 'undefined') return;

    graficoTendenciaCustos?.destroy();

    if (!scans || scans.length === 0) {
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        return;
    }

    // Ordena do mais antigo para o mais novo para linha temporal
    const scansCronologicos = [...scans].reverse();
    const labels = scansCronologicos.map((s) => formatarDataHora(s.createdAt));
    const custos = scansCronologicos.map((s) => Number(s.custoMensal));

    const ctx = canvas.getContext('2d');
    const gradient = ctx.createLinearGradient(0, 0, 0, 280);
    gradient.addColorStop(0, 'rgba(18, 59, 156, 0.25)');
    gradient.addColorStop(1, 'rgba(18, 59, 156, 0.01)');

    graficoTendenciaCustos = new Chart(canvas, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label: 'Custo Mensal Estimado',
                data: custos,
                borderColor: '#123b9c',
                borderWidth: 2.5,
                backgroundColor: gradient,
                fill: true,
                tension: 0.35,
                pointBackgroundColor: '#123b9c',
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2,
                pointRadius: 5,
                pointHoverRadius: 7,
            }],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        title: (items) => {
                            const index = items[0].dataIndex;
                            const scan = scansCronologicos[index];
                            return `${labels[index]} (${scan.nomeArquivo || 'resources.json'})`;
                        },
                        label: (context) => ` Custo estimado: ${formatarUsd(context.raw)}`,
                    },
                },
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: (valor) => formatarUsd(valor),
                    },
                    grid: {
                        color: 'rgba(224, 231, 240, 0.6)',
                    },
                },
                x: {
                    grid: {
                        display: false,
                    },
                },
            },
        },
    });
}

function renderizarTabelaHistorico(scans) {
    const tbody = document.getElementById('tabela-historico-corpo');
    if (!tbody) return;
    tbody.replaceChildren();

    if (!scans || scans.length === 0) {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td colspan="6" class="table-empty">Nenhum scan realizado ainda. Execute seu primeiro inventário na aba "Analisar estrutura".</td>';
        tbody.appendChild(tr);
        return;
    }

    scans.forEach((scan) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${escaparHtml(formatarDataHora(scan.createdAt))}</strong></td>
            <td><code>${escaparHtml(scan.nomeArquivo || 'resources.json')}</code></td>
            <td>${scan.totalRecursos}</td>
            <td>${scan.totalParadas}</td>
            <td><strong style="color: var(--blue);">${formatarUsd(scan.custoMensal)}</strong></td>
            <td class="text-right">
                <button type="button" class="btn-action-view" onclick="abrirModalDetalhes('${scan.id}')" title="Ver relatório completo">
                    <i class="fi fi-rr-eye" aria-hidden="true"></i> Relatório
                </button>
                <button type="button" class="btn-action-delete" onclick="excluirScan('${scan.id}')" title="Excluir este scan">
                    <i class="fi fi-rr-trash" aria-hidden="true"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function abrirModalDetalhes(scanId) {
    const modal = document.getElementById('modal-scan-detalhes');
    const conteudo = document.getElementById('modal-scan-conteudo');
    const tituloData = document.getElementById('modal-scan-data');
    if (!modal || !conteudo) return;

    modal.hidden = false;
    conteudo.innerHTML = '<p class="analise-loading"><span class="spinner" aria-hidden="true"></span> Carregando detalhes do scan…</p>';

    const token = await obterTokenAuth();
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;

    try {
        const resposta = await fetch(`/api/history/${scanId}`, { headers });
        if (!resposta.ok) {
            throw new Error(`Erro ao buscar scan (HTTP ${resposta.status})`);
        }
        const dados = await resposta.json();

        if (tituloData) {
            tituloData.textContent = `Scan de ${formatarDataHora(dados.createdAt)}`;
        }

        const recomendacaoHtml = dados.recomendacao ? formatarMarkdown(dados.recomendacao) : '<p>Sem recomendação de IA registrada.</p>';
        const recursos = dados.recursos || dados.instancias || [];

        let tabelaRecursosHtml = '';
        if (recursos.length > 0) {
            tabelaRecursosHtml = `
                <div class="table-wrap" style="margin-top: 24px;">
                    <h4>Recursos analisados</h4>
                    <table class="data-table">
                        <thead><tr><th>Recurso</th><th>Serviço</th><th>Tipo / estado</th><th>Custo mensal</th></tr></thead>
                        <tbody>
                            ${recursos.map(r => `
                                <tr>
                                    <td>${escaparHtml(String(r.id || '—'))}</td>
                                    <td>${escaparHtml(String(r.servico || 'EC2'))}</td>
                                    <td>${escaparHtml(String(r.tipo || '—'))}${r.estado ? ` / ${escaparHtml(String(r.estado))}` : ''}</td>
                                    <td>${formatarUsd(r.custoMensal)}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            `;
        }

        conteudo.innerHTML = `
            <div class="metrics" style="margin-bottom: 20px;">
                <article class="metric"><p class="metric-label">Recursos analisados</p><p class="metric-value">${dados.totalRecursos}</p></article>
                <article class="metric"><p class="metric-label">Potencialmente ociosos</p><p class="metric-value">${dados.totalParadas}</p></article>
                <article class="metric"><p class="metric-label">Custo mensal estimado</p><p class="metric-value metric-value--accent">${formatarUsd(dados.custoMensal)}</p></article>
            </div>
            <div class="recomendacao">
                <h4>Recomendação FinOps (Groq)</h4>
                <div class="recomendacao-conteudo">${recomendacaoHtml}</div>
            </div>
            ${tabelaRecursosHtml}
        `;
    } catch (e) {
        console.error(e);
        conteudo.innerHTML = `<p class="analise-erro" role="alert">Falha ao carregar relatório: ${escaparHtml(e.message)}</p>`;
    }
}

function fecharModalDetalhes() {
    const modal = document.getElementById('modal-scan-detalhes');
    if (modal) modal.hidden = true;
}

async function excluirScan(scanId) {
    if (!confirm('Deseja realmente excluir este scan do seu histórico? Esta ação é irreversível.')) {
        return;
    }

    const token = await obterTokenAuth();
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;

    try {
        const resposta = await fetch(`/api/history/${scanId}`, {
            method: 'DELETE',
            headers,
        });

        if (!resposta.ok) {
            throw new Error(`Erro ao excluir (HTTP ${resposta.status})`);
        }

        carregarHistorico();
    } catch (e) {
        alert(`Não foi possível excluir o scan: ${e.message}`);
    }
}

// ==============================================================================
// Gráficos de Análise e Markdown Helpers
// ==============================================================================
function prepararDadosGraficoRecursos(recursos) {
    const recursosComCusto = recursos
        .map((recurso, indice) => ({
            id: String(recurso.id ?? `Recurso ${indice + 1}`),
            custo: Number(recurso.custoMensal),
        }))
        .filter((recurso) => Number.isFinite(recurso.custo) && recurso.custo >= 0)
        .sort((a, b) => b.custo - a.custo);

    const principais = recursosComCusto.slice(0, LIMITE_RECURSOS_GRAFICO);
    const custoDemais = recursosComCusto
        .slice(LIMITE_RECURSOS_GRAFICO)
        .reduce((total, recurso) => total + recurso.custo, 0);

    if (custoDemais > 0) {
        principais.push({
            id: 'Demais recursos',
            custo: custoDemais,
            quantidade: recursosComCusto.length - LIMITE_RECURSOS_GRAFICO,
        });
    }

    return principais;
}

function criarElementoGrafico(tipo, classe, texto) {
    const elemento = document.createElement(tipo);
    elemento.className = classe;
    if (texto !== undefined) {
        elemento.textContent = texto;
    }
    return elemento;
}

function renderizarGraficosFallback(recursos) {
    const areaPizza = document.querySelector('.grafico-area--pizza');
    const areaRecursos = document.querySelector('#grafico-custos-recurso')?.parentElement;
    if (!areaPizza || !areaRecursos) {
        return;
    }

    const custosPorServico = new Map();
    const recursosComCusto = recursos
        .map((recurso, indice) => ({
            id: String(recurso.id ?? `Recurso ${indice + 1}`),
            servico: String(recurso.servico ?? 'Outros'),
            custo: Number(recurso.custoMensal),
        }))
        .filter((recurso) => Number.isFinite(recurso.custo) && recurso.custo >= 0);

    recursosComCusto.forEach((recurso) => {
        custosPorServico.set(recurso.servico, (custosPorServico.get(recurso.servico) ?? 0) + recurso.custo);
    });

    const cores = ['#123b9c', '#f59e0b', '#23815e', '#a855f7', '#ef4444', '#0891b2', '#64748b'];
    const servicos = [...custosPorServico.entries()];
    const total = servicos.reduce((soma, [, custo]) => soma + custo, 0);
    let inicio = 0;
    const fatias = servicos.map(([, custo], indice) => {
        const fim = inicio + (total ? custo / total * 100 : 0);
        const fatia = `${cores[indice % cores.length]} ${inicio}% ${fim}%`;
        inicio = fim;
        return fatia;
    });

    const pizza = criarElementoGrafico('div', 'grafico-fallback-pizza');
    pizza.style.background = total ? `conic-gradient(${fatias.join(', ')})` : '#e5e7eb';
    const legenda = criarElementoGrafico('div', 'grafico-fallback-legenda');
    servicos.forEach(([servico, custo], indice) => {
        const item = criarElementoGrafico('div', 'grafico-fallback-legenda-item');
        const marcador = criarElementoGrafico('span', 'grafico-fallback-marcador');
        marcador.style.backgroundColor = cores[indice % cores.length];
        item.append(marcador, criarElementoGrafico('span', '', `${servico}: ${formatarUsd(custo)}`));
        legenda.appendChild(item);
    });
    areaPizza.replaceChildren(pizza, legenda);

    const dadosGrafico = prepararDadosGraficoRecursos(recursos);
    const maiorCusto = Math.max(...dadosGrafico.map((recurso) => recurso.custo), 0);
    const barras = criarElementoGrafico('div', 'grafico-fallback-barras');
    dadosGrafico.forEach((recurso) => {
        const coluna = criarElementoGrafico('div', 'grafico-fallback-coluna');
        const barra = criarElementoGrafico('div', 'grafico-fallback-barra');
        barra.style.width = `${maiorCusto ? recurso.custo / maiorCusto * 100 : 0}%`;
        barra.title = `${recurso.id}: ${formatarUsd(recurso.custo)}`
            + (recurso.quantidade ? ` (${recurso.quantidade} recursos)` : '');
        coluna.append(barra, criarElementoGrafico('span', 'grafico-fallback-label', recurso.id));
        barras.appendChild(coluna);
    });
    areaRecursos.replaceChildren(barras);
}

function renderizarGraficos(recursos) {
    const elPizza = document.getElementById('grafico-custos-servico');
    const elRecurso = document.getElementById('grafico-custos-recurso');
    if (!elPizza || !elRecurso) return;

    if (typeof Chart === 'undefined') {
        console.error('Não foi possível carregar a biblioteca de gráficos.');
        renderizarGraficosFallback(recursos);
        return;
    }

    try {
        graficoCustosServico?.destroy();
        graficoCustosRecurso?.destroy();

        const custosPorServico = new Map();
        recursos.forEach((recurso) => {
            const custo = Number(recurso.custoMensal);
            if (!Number.isFinite(custo) || custo < 0) return;
            const servico = String(recurso.servico ?? 'Outros');
            custosPorServico.set(servico, (custosPorServico.get(servico) ?? 0) + custo);
        });

        const cores = ['#123b9c', '#f59e0b', '#23815e', '#a855f7', '#ef4444', '#0891b2', '#64748b'];
        const labelsServicos = [...custosPorServico.keys()];
        const valoresServicos = [...custosPorServico.values()];
        const dadosGrafico = prepararDadosGraficoRecursos(recursos);
        const labelsRecursos = dadosGrafico.map((recurso) => recurso.id);
        const custosRecursos = dadosGrafico.map((recurso) => recurso.custo);

        graficoCustosServico = new Chart(elPizza, {
            type: 'pie',
            data: {
                labels: labelsServicos,
                datasets: [{
                    data: valoresServicos,
                    backgroundColor: labelsServicos.map((_, indice) => cores[indice % cores.length]),
                    borderColor: '#ffffff',
                    borderWidth: 2,
                }],
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom' },
                    tooltip: { callbacks: { label: (context) => ` ${formatarUsd(context.raw)}` } },
                },
            },
        });

        graficoCustosRecurso = new Chart(elRecurso, {
            type: 'bar',
            data: {
                labels: labelsRecursos,
                datasets: [{
                    label: 'Custo mensal',
                    data: custosRecursos,
                    backgroundColor: '#123b9c',
                    borderRadius: 5,
                }],
            },
            options: {
                maintainAspectRatio: false,
                indexAxis: 'y',
                scales: {
                    y: {
                        ticks: {
                            autoSkip: false,
                            callback: (valor) => labelsRecursos[valor]?.length > 32
                                ? `${labelsRecursos[valor].slice(0, 29)}...`
                                : labelsRecursos[valor],
                        },
                    },
                    x: {
                        beginAtZero: true,
                        ticks: { callback: (valor) => formatarUsd(valor) },
                    },
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            title: (items) => {
                                const recurso = dadosGrafico[items[0].dataIndex];
                                return recurso.quantidade
                                    ? `${recurso.id} (${recurso.quantidade} recursos)`
                                    : recurso.id;
                            },
                            label: (context) => ` ${formatarUsd(context.raw)}`,
                        },
                    },
                },
            },
        });
    } catch (erro) {
        console.error('Não foi possível desenhar os gráficos:', erro);
        renderizarGraficosFallback(recursos);
    }
}

function mostrarErroAnalise(mensagem) {
    const el = document.getElementById('analise-erro');
    if (!el) return;
    el.textContent = mensagem;
    el.hidden = !mensagem;
}

function escaparHtml(texto) {
    return String(texto ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

function formatarInlineMarkdown(texto) {
    return escaparHtml(texto)
        .replace(/`([^`]+)`/g, '<code>$1</code>')
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/__(.+?)__/g, '<strong>$1</strong>')
        .replace(/(^|[^*\w])\*([^*\s](?:[^*]*[^*\s])?)\*(?![*\w])/g, '$1<em>$2</em>');
}

function formatarCelulaMarkdown(texto) {
    return formatarInlineMarkdown(texto.trim());
}

function ehSeparadorDeTabela(linha) {
    const celulas = linha.trim().replace(/^\||\|$/g, '').split('|');
    return celulas.length >= 2
        && celulas.every((celula) => /^\s*:?-{3,}:?\s*$/.test(celula));
}

function separarCelulasTabela(linha) {
    return linha.trim().replace(/^\||\|$/g, '').split('|');
}

function ehTituloPrioridade(linha) {
    return linha.replace(/[*_:#]/g, '').trim().toLowerCase() === 'prioridade de ação';
}

function classePrioridadeSegura(texto) {
    const valor = String(texto ?? '').trim().toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '');
    if (valor === 'alta') return 'prioridade prioridade--alta';
    if (valor === 'media') return 'prioridade prioridade--media';
    if (valor === 'baixa') return 'prioridade prioridade--baixa';
    return 'prioridade';
}

function renderizarTabelaPrioridade(acao, prioridade, justificativa) {
    return `<div class="markdown-table-wrap"><table class="markdown-table">
        <thead><tr><th scope="col">Ação</th><th scope="col">Prioridade</th><th scope="col">Por quê</th></tr></thead>
        <tbody><tr>
            <td>${formatarCelulaMarkdown(acao)}</td>
            <td class="${classePrioridadeSegura(prioridade)}">${formatarCelulaMarkdown(prioridade)}</td>
            <td>${formatarCelulaMarkdown(justificativa)}</td>
        </tr></tbody>
    </table></div>`;
}

function formatarMarkdown(texto) {
    if (!texto) return '';
    const linhas = texto.split(/\r?\n/);
    const html = [];
    let listaAtual = null;

    const fecharLista = () => {
        if (listaAtual) {
            html.push(`</${listaAtual}>`);
            listaAtual = null;
        }
    };

    const formatarLinha = formatarInlineMarkdown;

    for (let indice = 0; indice < linhas.length; indice++) {
        const linha = linhas[indice];
        const conteudo = linha.trim();
        const itemNumerado = conteudo.match(/^\d+\.\s+(.+)$/);
        const itemMarcado = conteudo.match(/^[-*]\s+(.+)$/);

        if (/^(-{3,}|\*{3,}|_{3,})$/.test(conteudo)) {
            fecharLista();
            html.push('<hr>');
            continue;
        }

        if (ehTituloPrioridade(conteudo)
            && linhas[indice + 2]
            && /^[-*]\s+/.test(linhas[indice + 1].trim())
            && /^[-*]\s+/.test(linhas[indice + 2].trim())) {
            fecharLista();
            const prioridadeTexto = linhas[indice + 1].trim().replace(/^[-*]\s+/, '');
            const prioridade = prioridadeTexto.match(/\b(alta|m[ée]dia|baixa)\b/i)?.[1] ?? 'Baixa';
            const acao = 'Ação recomendada';
            const justificativa = linhas[indice + 2].trim().replace(/^[-*]\s+/, '');
            html.push(`<p><strong>Prioridade de ação</strong></p>`);
            html.push(renderizarTabelaPrioridade(acao, prioridade, justificativa));
            indice += 2;
            continue;
        }

        if (conteudo.includes('|') && linhas[indice + 1]
            && ehSeparadorDeTabela(linhas[indice + 1])) {
            fecharLista();
            const cabecalho = separarCelulasTabela(conteudo);
            const indicePrioridade = cabecalho.findIndex((celula) =>
                celula.trim().toLowerCase() === 'prioridade');
            const linhasTabela = [];
            let indiceLinha = indice + 2;

            while (indiceLinha < linhas.length
                && linhas[indiceLinha].trim().includes('|')
                && linhas[indiceLinha].trim() !== '') {
                linhasTabela.push(separarCelulasTabela(linhas[indiceLinha]));
                indiceLinha++;
            }

            html.push('<div class="markdown-table-wrap"><table class="markdown-table"><thead><tr>');
            html.push(cabecalho.map((celula) => `<th scope="col">${formatarCelulaMarkdown(celula)}</th>`).join(''));
            html.push('</tr></thead><tbody>');
            linhasTabela.forEach((celulas) => {
                html.push('<tr>');
                cabecalho.forEach((_, indiceCelula) => {
                    const celula = celulas[indiceCelula] ?? '';
                    const classePrioridade = indiceCelula === indicePrioridade
                        ? ` class="${classePrioridadeSegura(celula)}"`
                        : '';
                    html.push(`<td${classePrioridade}>${formatarCelulaMarkdown(celula)}</td>`);
                });
                html.push('</tr>');
            });
            html.push('</tbody></table></div>');
            indice = indiceLinha - 1;
            continue;
        }

        if (itemNumerado || itemMarcado) {
            const tipo = itemNumerado ? 'ol' : 'ul';
            const item = itemNumerado ? itemNumerado[1] : itemMarcado[1];
            if (listaAtual !== tipo) {
                fecharLista();
                html.push(`<${tipo}>`);
                listaAtual = tipo;
            }
            html.push(`<li>${formatarLinha(item)}</li>`);
        } else if (conteudo) {
            fecharLista();
            html.push(`<p>${formatarLinha(conteudo)}</p>`);
        } else {
            fecharLista();
        }
    }

    fecharLista();
    return html.join('');
}

function renderizarResultado(dados) {
    document.getElementById('res-total-recursos').textContent =
        dados.totalRecursos ?? (dados.instancias?.length ?? 0);
    document.getElementById('res-total-paradas').textContent = dados.totalParadas ?? 0;
    document.getElementById('res-custo-mensal').textContent = formatarUsd(dados.custoMensal);

    const recomendacao = dados.recomendacao ?? 'Sem recomendação.';
    document.getElementById('res-recomendacao').innerHTML = formatarMarkdown(recomendacao);

    const tbody = document.getElementById('res-tabela-recursos');
    tbody.replaceChildren();

    const lista = dados.recursos ?? dados.instancias ?? [];
    renderizarGraficos(lista);
    if (lista.length === 0) {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td colspan="4" class="table-empty">Nenhum recurso encontrado.</td>';
        tbody.appendChild(tr);
    } else {
        for (const item of lista) {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${escaparHtml(String(item.id ?? '—'))}</td>
                <td>${escaparHtml(String(item.servico ?? 'EC2'))}</td>
                <td>${escaparHtml(String(item.tipo ?? '—'))}${item.estado ? ` / ${escaparHtml(String(item.estado))}` : ''}</td>
                <td>${formatarUsd(item.custoMensal)}</td>
            `;
            tbody.appendChild(tr);
        }
    }

    document.getElementById('resultado-analise').hidden = false;

    const salvoAlerta = document.getElementById('analise-sucesso-salvo');
    if (salvoAlerta && dados.scanId) {
        salvoAlerta.hidden = false;
    }
}

async function enviarAnalise(event) {
    event.preventDefault();

    const input = document.getElementById('arquivo-json');
    const btn = document.getElementById('btn-analisar');
    const carregando = document.getElementById('analise-carregando');
    const salvoAlerta = document.getElementById('analise-sucesso-salvo');

    mostrarErroAnalise('');
    document.getElementById('resultado-analise').hidden = true;
    if (salvoAlerta) salvoAlerta.hidden = true;

    if (!input?.files?.length) {
        mostrarErroAnalise('Selecione um arquivo JSON.');
        return;
    }

    const arquivo = input.files[0];
    if (arquivo.size > LIMITE_TAMANHO_ARQUIVO_BYTES) {
        mostrarErroAnalise(
            `Arquivo grande demais (${Math.ceil(arquivo.size / 1024)} KB). `
                + `Inventários típicos têm cerca de 3 KB; o limite é ${LIMITE_TAMANHO_ARQUIVO_KB} KB.`
        );
        return;
    }

    // Proteção de rotas: se autenticação for obrigatória e o usuário não estiver logado
    if (authRequired && !currentUser) {
        mostrarErroAnalise('Acesso restrito: você precisa estar autenticado para enviar inventários.');
        abrirModalAuth('login');
        return;
    }

    const formData = new FormData();
    formData.append('file', arquivo);
    const usarIa = document.getElementById('usar-ia')?.checked === true;
    formData.append('usarIa', usarIa ? 'true' : 'false');

    const headers = {};
    const apiKey = window.CLOUDSAVER_API_KEY || localStorage.getItem('cloudsaverApiKey') || '';
    if (apiKey) {
        headers['X-API-Key'] = apiKey;
    }

    const token = await obterTokenAuth();
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    btn.disabled = true;
    carregando.hidden = false;

    try {
        const resposta = await fetch('/api/analise', {
            method: 'POST',
            headers,
            body: formData,
        });

        if (resposta.status === 401) {
            mostrarErroAnalise('Sessão expirada ou não autenticada. Faça login novamente.');
            abrirModalAuth('login');
            return;
        }

        if (!resposta.ok) {
            const texto = await resposta.text();
            mostrarErroAnalise(
                texto || `Erro na análise (HTTP ${resposta.status}). Verifique o JSON.`
            );
            return;
        }

        const dados = await resposta.json();
        renderizarResultado(dados);
    } catch (erro) {
        console.error(erro);
        mostrarErroAnalise('Falha de rede ao conectar com a aplicação.');
    } finally {
        btn.disabled = false;
        carregando.hidden = true;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    inicializarSupabase();

    const form = document.getElementById('form-analise');
    form?.addEventListener('submit', enviarAnalise);

    const input = document.getElementById('arquivo-json');
    const nomeArquivo = document.getElementById('nome-arquivo');
    input?.addEventListener('change', () => {
        const file = input.files?.[0];
        if (nomeArquivo) {
            nomeArquivo.textContent = file ? file.name : 'Nenhum arquivo selecionado';
        }
    });

    // Fechar modais ao clicar no backdrop escuro
    document.querySelectorAll('.modal-backdrop').forEach((backdrop) => {
        backdrop.addEventListener('click', (event) => {
            if (event.target === backdrop) {
                backdrop.hidden = true;
            }
        });
    });

    // Fechar com tecla ESC
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            document.querySelectorAll('.modal-backdrop').forEach((backdrop) => {
                backdrop.hidden = true;
            });
        }
    });

    const links = [...document.querySelectorAll('.side-nav-link')];
    const atualizarMenu = () => {
        const paginaAtual = window.location.pathname.split('/').pop() || 'index.html';
        links.forEach((link) => {
            const destino = link.getAttribute('href')?.split('#')[0];
            link.classList.toggle('is-active', destino === paginaAtual);
        });
    };

    window.addEventListener('hashchange', atualizarMenu);
    atualizarMenu();
});
