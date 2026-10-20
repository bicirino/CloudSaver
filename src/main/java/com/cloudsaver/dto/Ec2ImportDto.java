package main.java.com.cloudsaver.dto; 

import com.fasterxml.jackson.annotation.JsonProperty; 
import lombok.Getter; 
import lombok.Setter; 

import java.util.List; 

/* 
    Servirá como estrutura de tradução da entrada JSON do AWS para um objeto Java 

    Basicamente, esse arquivo funciona como um filtro de entrada: captura apenas os campos necessários para identificar e analisar os recursos AWS

    A anotação @JsonProperty é usada para mapear os nomes dos campos do JSON para os nomes dos campos da classe Java 
*/


@Getter 
@Setter 
public class Ec2ImportDto {

    @JsonProperty("InstanceId")
    private String instanceId; 

    @JsonProperty("InstanceType")
    private String instanceType; 

    @JsonProperty("State")
    private StateDto state; 

    @JsonProperty("Placement")
    private Placement placement; 

    @JsonProperty("LaunchTime")
    private String launchTime;

    @JsonProperty("Tags")
    private List<TagDto> tags;

    @Getter 
    @Setter 
    public static class StateDto { 

        @JsonProperty("Name")
        private String name;  
    }

    @Getter 
    @Setter 
    public static class PlacementDto { 

        @JsonProperty("AvailabilityZone") 
        private String availabilityZone; 
    }

    @Getter 
    @Setter 
    public static class TagDto { 

        @JsonProperty("Key") 
        private String key; 

        @JsonProperty("Value") 
        private String value;
    }

    
}
