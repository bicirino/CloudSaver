#_________________________________________________________________
## ETAPA DE COMPILAÇÃO 

# Define a imagem base para a primeira etapa de compilação 
FROM maven:3.9-eclipse-temurin-17 AS builder                             

# Diretório de trabalho dentro do container
WORKDIR /app

# Copia arquivo de configuração do Maven para o diretório de trabalho 
COPY pom.xml . 
# Copia o código-fonte do projeto para o diretório de trabalho
COPY src ./src

# Executa comando Maven de empacotamento do projeto, ignorando os testes
RUN mvn clean package -DskipTests

#_________________________________________________________________
# ETAPA DE EXECUÇÃO (RUNTIME)

# Define a imagem base para a segunda etapa de execução
FROM eclipse-temurin:17-jre 

# Define o diretório de trabalho dentro do container
WORKDIR /app

# Ele copia apenas o arquivo .jar gerado na etapa anterior e o renomeia para app.jar
COPY --from=builder /app/target/*.jar app.jar

# A porta que o container expõe será a 8080, que é a porta padrão para aplicações web
EXPOSE 8080

# Comando padrão que será executado automaticamente toda vez que um container for iniciado a partir desta imagem
ENTRYPOINT ["java", "-jar", "app.jar"]