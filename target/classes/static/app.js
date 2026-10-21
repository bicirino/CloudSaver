function realizarLogin(event){ 

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