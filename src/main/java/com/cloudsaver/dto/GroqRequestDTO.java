package com.cloudsaver.dto; 

// Bibliotecas Lombok para criação de getters, setters, construtores, etc. 
import lombok.Builder; 
import lombok.Data; 

import java.util.List; 

/* 

DTO (Data Transfer Object) para montar a requisição enviada ao GROQ API 

Mapeia (associa) o formato JSON esperado pela API do GROQ com as propriedades da classe Java  

*/ 

@Data 
@Builder 
public class GroqRequestDto { 

    // Armazena nome do modelo da IA 
    private String model; 

    // Armazena a lista de mensagens (conversa) enviada para a IA
    private List<Message> messages; 

    public GroqRequestDTO() {
    }

    public GroqRequestDTO(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
    }


    @Data
    @Builder 
    public static class Message { 

        // Papel do emissor da mensagem 
        private String role; 

        // Conteúdo do prompt enviado ao modelo 
        private String content; 

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}