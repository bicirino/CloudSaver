package com.cloudsaver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

// Representa uma instância EC2 lida diretamente do JSON gerado pelo AWS CLI.
// @JsonIgnoreProperties ignora campos extras do JSON sem quebrar a leitura.
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanciaEc2 {

    @JsonProperty("InstanceId")
    private String instanceId;

    @JsonProperty("InstanceType")
    private String instanceType;

    // Pode vir como objeto { "Name": "stopped" } ou como String pura, dependendo da query usada.
    @JsonProperty("State")
    private Object state;

    // Retorna true se o estado da instância é "stopped".
    public boolean isParada() {
        if (state instanceof String s) {
            return "stopped".equalsIgnoreCase(s.trim());
        }
        if (state instanceof java.util.Map<?, ?> map) {
            Object name = map.get("Name");
            return name instanceof String s && "stopped".equalsIgnoreCase(s.trim());
        }
        return false;
    }
}
