package main.java.com.cloudsaver.service; 

import com.cloudsaver.dto.Ec2ImportDto; 
import org.springframework.stereotype.Service; 

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service 
public class CostCalculatorService {

    // Tabela de preços estimados por hora em USD 
    private static final Map<String, BigDecimal> HOURLY_RATES = new HashMap<>(); 

    // Preços estimados por hora de diferentes tipos de instâncias EC2 
    // "static" é usado para inicializar o HashMap de forma estática 
    static {
        HOURLY_RATES.put("t2.micro", new BigDecimal("0.0116"));
        HOURLY_RATES.put("t3.micro", new BigDecimal("0.0104"));
        HOURLY_RATES.put("t3.small", new BigDecimal("0.0208"));
        HOURLY_RATES.put("t3.medium", new BigDecimal("0.0416"));
        HOURLY_RATES.put("t3.large", new BigDecimal("0.0832"));
        HOURLY_RATES.put("m5.large", new BigDecimal("0.0960"));
        HOURLY_RATES.put("m5.xlarge", new BigDecimal("0.1920"));
        HOURLY_RATES.put("c5.large", new BigDecimal("0.0850"));
        HOURLY_RATES.put("r5.large", new BigDecimal("0.1260"));
    }

    private static final BigDecimal DEFAULT_HOURLY_RATE = new BigDecimal("0.0500");
    private static final BigDecimal HOURS_IN_MONTH = new BigDecimal("730"); // Média mensal de horas AWS


    public BigDecimal calculateMonthlyCost(List<Ec2ImportDto> instances) {

        if (instanceType == null){ 
            instanceType = ""; 
        }

        BigDecimal hourlyRate = HOURLY_RATES.getOrDefault{ 
            instanceType.toLowerCase(), 
            DEFAULT_HOURLY_RATE 
        }; 

        // Preço/hora * 730 horas = custo mensal 
        return hourlyRate.multiply(HOURS_IN_MONTH).setScale(2, RoundingMode.HALF_UP);
    } 

    // Calcula o custo mensal de desperdício para todas as instâncias paradas 
    public BigDecimal calculateTotalMonthlyWaste(List<Ec2ImportDto> stoppedInstances) {
        return stoppedInstances.stream()
                .map(instance -> calculateMonthlyWaste(instance.getInstanceType()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

} 
