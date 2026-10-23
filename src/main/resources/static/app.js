function realizarLogin(event) {

    // Impede a página de recarregar automaticamente 
    event.preventDefault();


    window.location.href = "index.html";
}


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

    // Armazena o texto original do botão para restaurá-lo posteriormente 
    const originalText = button.innerText;

    // Tenta copiar o texto do elemento <code> para a área de transferência 
    try {
        await navigator.clipboard.writeText(codeElement.innerText);
        button.innerText = '✓';
        button.setAttribute('aria-label', 'Comando copiado');
    } catch (error) {
        console.error('Erro ao copiar:', error);
        button.innerText = '!';
        button.setAttribute('aria-label', 'Erro ao copiar comando');
    }

    // Define um temprizador para restaurar o texto original do botão após 2 segundos 
    setTimeout(() => {
        button.innerText = originalText;
        button.setAttribute('aria-label', 'Copiar comando');
    }, 2000);
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

function renderizarResultado(dados) {
    document.getElementById('res-total-paradas').textContent = dados.totalParadas ?? 0;
    document.getElementById('res-custo-mensal').textContent = formatarUsd(dados.custoMensal);

    const recomendacao = dados.recomendacao ?? 'Sem recomendação.';
    document.getElementById('res-recomendacao').textContent = recomendacao;

    const tbody = document.getElementById('res-tabela-instancias');
    tbody.replaceChildren();

    const lista = dados.instancias ?? [];
    if (lista.length === 0) {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td colspan="3" class="table-empty">Nenhuma instância parada encontrada.</td>';
        tbody.appendChild(tr);
    } else {
        for (const item of lista) {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${item.id ?? '—'}</td>
                <td>${item.tipo ?? '—'}</td>
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