package com.cloudsaver; 

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Anotação principal do Spring Boot 
@SpringBootApplication 

// Habilita o agendamento de tarefas 
@EnableScheduling 

public class CloudSaverApplication { 

    public static void main(String[] args){ 

        // Inicia a aplicação Spring Boot 
        SpringApplication.run(CloudSaverApplication.class, args);
        System.out.println("CloudSaver Application started");

    }


} 