package com.cloudsaver.dto; 

// Bibliotecas Lombok para criação de getters, setters, construtores, etc. 
import lombok.AllArgsConstructor; 
import lombok.Builder; 
import lombok.Data; 
import lombok.NoArgsConstructor; 

import java.util.List; 

/* 

DTO (Data Transfer Object) para montar a requisição enviada ao GROQ API 

Mapeia (associa) o formato JSON esperado pela API do GROQ com as propriedades da classe Java  

*/ 

@Data 
@Builder 
@AllArgsConstructor 
@NoArgsConstructor 
public class GroqRequestDTO { 

    // Armazena nome do modelo da IA 
    private String model; 

    // Armazena a lista de mensagens (conversa) enviada para a IA
    private List<Message> messages; 


    @Data
    @Builder 
    @AllArgsConstructor 
    @NoArgsConstructor 
    public static class Message { 

        // Papel do emissor da mensagem 
        private String role; 

        // Conteúdo do prompt enviado ao modelo 
        private String content; 
    }
}