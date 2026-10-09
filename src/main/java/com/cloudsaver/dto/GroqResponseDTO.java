package com.cloudsaver.dto; 

import lombok.AllArgsConstructor; 
import lombok.Data; 
import lombok.NoArgsConstructor;  

import java.util.List; 

/* 

DTO para deserializar (converter) a resposta JSON devolvida pela API da GROQ 

*/ 

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroqResponseDTO { 

    // Armazena a lista de escolhas (respostas) geradas pela IA 
    private List<Choice> choices; 

    @Data 
    @AllArgsConstructor 
    @NoArgsConstructor 
    public static class Choice { 

        // Armazena a mensagem (resposta) gerada pela IA 
        private Message message; 

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor 
    public static class Message { 

        // Papel do emissor da mensagem 
        private String role;

        // Conteúdo da mensagem (resposta) gerada pela IA 
        private String content;  
    }

}