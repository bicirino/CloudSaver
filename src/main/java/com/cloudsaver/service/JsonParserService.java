package main.java.com.cloudsaver.service;

import com.cloudsaver.dto.Ec2ImportDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/* 

    Esse arquivo será responsável por: 
        
        Ler o arquivo .json recebido da AWS
        Converter o conteúdo do arquivo para uma lista de objetos Java (Ec2ImportDto)
        Filtrar e extrair apenas as instâncias EC2 que estão ociosas (paradas)  

*/

@Service
public class JsonParserService {

    private final ObjectMapper objectMapper;

    public JsonParserService() {
        this.objectMapper = new ObjectMapper();
        // Configuração para ignorar campos desconhecidos do JSON da AWS sem quebrar a aplicação
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Processa o arquivo JSON retornado pelo comando AWS CLI e filtra instâncias paradas.
     */
    public List<Ec2ImportDto> parseEc2Instances(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("O arquivo enviado está vazio.");
        }

        try {
            List<Ec2ImportDto> instances = objectMapper.readValue(
                    file.getInputStream(),
                    new TypeReference<List<Ec2ImportDto>>() {}
            );

            if (instances == null) {
                return new ArrayList<>();
            }

            // Filtra mantendo apenas instâncias no estado "stopped"
            return instances.stream()
                    .filter(instance -> instance.getState() != null 
                            && "stopped".equalsIgnoreCase(instance.getState().getName()))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException("Falha ao ler e processar o arquivo JSON da AWS: " + e.getMessage(), e);
        }
    }
}