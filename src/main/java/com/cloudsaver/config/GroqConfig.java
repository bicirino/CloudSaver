package main.java.com.cloudsaver.config; 

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/* 

    Classe de configuração para o cliente HTTP responsável pela comunicação com a API da Groq AI.  

*/
@Configuration 
public class GroqConfig {

    // URL base da API da Groq, podendo ser configurado via arquivo de propriedades 
    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl; 

    // Chave de autenticação da API da Groq, podendo ser configurada via arquivo de propriedades
    @Value("${groq.api.key}")
    private String groqApiKey; 



    /* 
        Cria e injeta um RestCliente configurado com os cabeçalhos de autenticação 
        Isso é necessário para enviar o prompt e o JSON para a GROQ AI 
        '@Bean' indica que o método retorna um objeto gerenciado pelo Spring Boot, que pode ser injetado em outras classes 
    */ 
    @Bean 
    public RestCliente groqRestCliente(){ 

        return RestCliente.builder()
                .baseUrl(groqApiUrl) 
                .defaultHeader("Authorization", "Bearer " + groqApiKey) 
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
}
