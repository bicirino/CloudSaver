// Cria uma função assíncrona (executa em segundo plano) para copiar o comando de código 
async function copyCode(button) {

    // Localiza o elemento pai mais próximo com a classe 'comando' e, em seguida, procura o elemento <code> dentro dele 
    const container = button.closest('.comando');
    const codeElement = container?.querySelector('code');

    // Se o elemento <code> não for encontrado, exibe um erro no console 
    if (!codeElement) {
        console.error('Não foi possível localizar o comando para copiar.');
        return;
    }

    // Tenta copiar o texto do elemento <code> para a área de transferência
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

function mostrarErroAnalise(mensagem) {
    const el = document.getElementById('analise-erro');
    if (!el) return;
    el.textContent = mensagem;
    el.hidden = !mensagem;
}

function escaparHtml(texto) {
    return texto
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

function formatarCelulaMarkdown(texto) {
    return escaparHtml(texto.trim())
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/__(.+?)__/g, '<strong>$1</strong>');
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

function renderizarTabelaPrioridade(acao, prioridade, justificativa) {
    return `<div class="markdown-table-wrap"><table class="markdown-table">
        <thead><tr><th scope="col">Ação</th><th scope="col">Prioridade</th><th scope="col">Por quê</th></tr></thead>
        <tbody><tr>
            <td>${formatarCelulaMarkdown(acao)}</td>
            <td class="prioridade prioridade--${prioridade.toLowerCase()}">${formatarCelulaMarkdown(prioridade)}</td>
            <td>${formatarCelulaMarkdown(justificativa)}</td>
        </tr></tbody>
    </table></div>`;
}

function formatarMarkdown(texto) {
    const linhas = texto.split(/\r?\n/);
    const html = [];
    let listaAtual = null;

    const fecharLista = () => {
        if (listaAtual) {
            html.push(`</${listaAtual}>`);
            listaAtual = null;
        }
    };

    const formatarLinha = (linha) => escaparHtml(linha)
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/__(.+?)__/g, '<strong>$1</strong>');

    for (let indice = 0; indice < linhas.length; indice++) {
        const linha = linhas[indice];
        const conteudo = linha.trim();
        const itemNumerado = conteudo.match(/^\d+\.\s+(.+)$/);
        const itemMarcado = conteudo.match(/^[-*]\s+(.+)$/);

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
                    const classePrioridade = indiceCelula === 1
                        ? ` class="prioridade prioridade--${celula.trim().toLowerCase()}"`
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
}

async function enviarAnalise(event) {
    event.preventDefault();

    const input = document.getElementById('arquivo-json');
    const btn = document.getElementById('btn-analisar');
    const carregando = document.getElementById('analise-carregando');

    mostrarErroAnalise('');
    document.getElementById('resultado-analise').hidden = true;

    if (!input?.files?.length) {
        mostrarErroAnalise('Selecione um arquivo JSON.');
        return;
    }

    const formData = new FormData();
    formData.append('file', input.files[0]);

    btn.disabled = true;
    carregando.hidden = false;

    try {
        const resposta = await fetch('/api/analise', {
            method: 'POST',
            body: formData,
        });

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
        mostrarErroAnalise('Falha de rede. A aplicação está rodando em http://localhost:8080?');
    } finally {
        btn.disabled = false;
        carregando.hidden = true;
    }
}

document.addEventListener('DOMContentLoaded', () => {
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