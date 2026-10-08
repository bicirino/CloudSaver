# ☁️ CloudSaver - AWS Resource Optmizer & AI Advisor 

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-brightgreen?style=for-the-badge&logo=springboot)
![AWS SDK](https://img.shields.io/badge/AWS_SDK-v2-FF9900?style=for-the-badge&logo=amazonaws)
![Groq AI](https://img.shields.io/badge/Groq_AI-LLM-blueviolet?style=for-the-badge)
![HTML5 & CSS3](https://img.shields.io/badge/Frontend-HTML5%20%2F%20CSS3-E34F26?style=for-the-badge&logo=html5)
![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker)
![Kubernetes](https://img.shields.io/badge/Kubernetes-Orchestrated-326CE5?style=for-the-badge&logo=kubernetes)
![Jenkins](https://img.shields.io/badge/Jenkins-Automated_Pipeline-D24939?style=for-the-badge&logo=jenkins)


Plataforma Cloud-Native para identificação automatizada de recursos ociosos na AWS, cálculo de desperdício financeiro e geração de diagnósticos estratégicos via LLM, acompanhada de um dashboard intuitivo e interativo 


## 📌 1. Visão Geral do Projeto

No cenário moderno de engenharia de nuvem, a falta de visibilidade sobre recursos ociosos (como instâncias EC2 desligadas mantendo custos de armazenamento, volumes EBS desanexados e IP Elastic não associados) pode gerar **custos desnecessários significativos**.

O **CloudSaver** é uma solução completa desenvolvida em **Java** e **Spring Boot**. A aplicação realiza varreduras periódicas na infraestrutura **AWS**, quantifica o desperdício financeiro mensal estimado, utiliza a **Groq AI (LLM)** para gerar relatórios de otimização e disponibiliza uma **interface gráfica web leve e intuitiva** (HTML/CSS/JS) servida diretamente pelo Spring Boot.

Todo o ciclo de compilação, testes, empacotamento em contentores e deploy em cluster **Kubernetes / OpenShift** é automatizado por pipelines declarativas no **Jenkins**.

---

## 🛠️ 2. Stacks e Tecnologias Utilizadas

### **Backend & Engine Integrada**
* **Linguagem & Framework**: Java 21, Spring Boot 3.x (Spring Web, Spring Data JPA, Spring Validation, Spring Scheduler)
* **Gestor de Dependências**: Apache Maven
* **SDK Cloud**: AWS SDK for Java v2 (`software.amazon.awssdk:ec2`, `software.amazon.awssdk:cloudwatch`)
* **Integração de IA**: Client HTTP para a API da **Groq AI** (Llama 3 / Mixtral para engenharia de prompts FinOps)

### **Interface Gráfica (Frontend Embutido)**
* **Tecnologias**: HTML5, CSS3 (Design moderno com Flexbox/Grid e variáveis CSS) e Vanilla JavaScript (Fetch API)
* **Recursos Visuais**: Chart.js (via CDN para gráficos de custos) e FontAwesome (ícones para recursos AWS)
* **Serviço**: Ficheiros estáticos servidos diretamente pela pasta `src/main/resources/static/` do Spring Boot (porta `8080`)

### **Persistência de Dados**
* **Produção / Dev**: PostgreSQL
* **Ambiente de Teste**: SQLite / H2 Database

### **DevOps & CI/CD Pipeline**
* **Automação de CI/CD**: **Jenkins** (Pipeline de Build, Test, Dockerize e Deploy)
* **Conteinerização**: Docker (Multi-stage build para a geração do JAR único)
* **Orquestração**: Kubernetes (Manifestos de Deployment, Service, ConfigMap, Secret)
* **Emulação Cloud Local**: LocalStack (Simulação de serviços AWS em ambiente de desenvolvimento local)
* **Observabilidade**: Spring Boot Actuator, Prometheus e Grafana

---

## 🖥️ 3. Interface Gráfica Web

A aplicação conta com um painel web responsivo e limpo, sem necessidade de dependências complexas de *build* no frontend

### **Recursos da Interface**:
* **Cards Executivos**: Indicadores em tempo real do custo mensal jogado fora e total de recursos ociosos.
* **Painel de IA FinOps**: Bloco em destaque apresentando a análise em linguagem natural gerada pela **Groq AI**.
* **Gráfico de Evolução**: Visualização temporal do desperdício acumulado utilizando Chart.js.
* **Tabela de Recursos**: Listagem interativa de instâncias EC2 e volumes EBS com detalhes e botão para **"Disparar Varredura Sob Demanda"**.

---

## ⚙️ 4. Esteira de CI/CD com Jenkins

A automação da entrega contínua é gerida pelo ficheiro `Jenkinsfile` presente na raiz do projeto, estruturado nas seguintes etapas:

```text
[ Git Checkout ] ➔ [ Maven Compile & Test ] ➔ [ Build JAR ] ➔ [ Docker Build & Push ] ➔ [ K8s / OpenShift Deploy ]
```

1. **Checkout**: Clonagem automatizada do código fonte a partir do repositório Git.
2. **Compile & Test**: Validação do código e execução de testes unitários com **Apache Maven** e JUnit 5.
3. **Build & Package**: Empacotamento do artefacto executável `.jar` contendo a API e os ficheiros estáticos da interface web.
4. **Docker Image Build**: Construção da imagem OCI utilizando o `Dockerfile` multi-stage e envio para o Docker Registry com *tagging* automático por *build number*.
5. **Deploy Automatizado**: Aplicação dos manifestos YAML no cluster **Kubernetes** ou **Red Hat OpenShift**.

---

## 📁 5. Estrutura de Pastas do Repositório

```text
cloudsaver-finops/
├── Jenkinsfile                        # Pipeline Declarativa do Jenkins (CI/CD)
├── docker/
│   ├── Dockerfile                     # Multi-stage Dockerfile para aplicação unificada
│   └── docker-compose.yml             # Subida local da infraestrutura (App + PostgreSQL + LocalStack)
├── k8s/
│   ├── configmap.yaml                 # Configurações genéricas do ambiente
│   ├── secret.yaml.template           # Template para chaves da AWS e Groq AI
│   ├── deployment.yaml                # Especificação do Deployment da aplicação
│   └── service.yaml                   # Exposição interna do serviço Kubernetes
├── src/
│   ├── main/
│   │   ├── java/com/cloudsaver/finops/
│   │   │   ├── config/                # Configurações AWS, RestTemplate e OpenAPI
│   │   │   ├── controller/            # Endpoints REST da API FinOps
│   │   │   ├── dto/                   # Data Transfer Objects (Requests/Responses)
│   │   │   ├── model/                 # Entidades JPA (Resource, ScanReport)
│   │   │   ├── repository/            # Interfaces de comunicação com a Base de Dados
│   │   │   ├── service/               # Regra de negócio (AwsScanService, CostCalculatorService, GroqAiService)
│   │   │   └── scheduler/             # Agendador de tarefas periódicas (@Scheduled)
│   │   └── resources/
│   │       ├── application.yml        # Configurações do Spring Boot
│   │       ├── db/migration/          # Scripts de DDL/Migração (Flyway)
│   │       └── static/                # INTERFACE GRÁFICA WEB (HTML / CSS / JS)
│   │           ├── css/
│   │           │   └── styles.css     # Estilos da interface web
│   │           ├── js/
│   │           │   └── app.js         # Lógica de consumo da API REST e montagem do dashboard
│   │           └── index.html         # Painel principal do utilizador
│   └── test/                          # Testes unitários e de integração (JUnit 5, Mockito)
├── pom.xml                            # Ficheiro de configuração Maven
└── README.md                          # Documentação do projeto
```

---

## 🚀 6. Processo de Configuração e Execução Passo a Passo

### **Pré-requisitos**
* Java 21 JDK instalado
* Apache Maven 3.8+
* Docker e Docker Compose
* Servidor Jenkins configurado (local ou via contentor)
* Chave de API da Groq AI (`GROQ_API_KEY`)
* AWS CLI configurado ou LocalStack para testes locais

---

### **Passo 1: Clonar o Repositório e Configurar Variáveis**
```bash
git clone https://github.com/seu-usuario/cloudsaver-finops.git
cd cloudsaver-finops
```

Crie um ficheiro `.env` na raiz do projeto com base no modelo:
```env
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
GROQ_API_KEY=sua_chave_groq_ai_aqui
DB_USERNAME=finops_user
DB_PASSWORD=finops_pass
```

---

### **Passo 2: Executar em Modo de Desenvolvimento (Local)**
Pode executar a aplicação diretamente através do Maven:
```bash
mvn clean install
mvn spring-boot:run
```

Após a inicialização, aceda ao painel gráfico através do browser:
👉 **`http://localhost:8080`**

---

### **Passo 3: Executar via Docker Compose (Aplicação + PostgreSQL + LocalStack)**
Para subir todo o ambiente de forma isolada em contentores:
```bash
docker-compose -f docker/docker-compose.yml up -d
```

Aceda à interface web em `http://localhost:8080`.

---

### **Passo 4: Configurar o Job no Jenkins**
1. No painel do Jenkins, crie um novo item do tipo **Pipeline**.
2. Na seção **Pipeline**, selecione *Pipeline script from SCM*.
3. Defina o SCM como **Git**, insira o URL do repositório e aponte o caminho do Script Path para `Jenkinsfile`.
4. Salve e clique em **Build Now** para disparar a esteira automatizada.

---

## 🔗 7. Documentação da API REST (Endpoints)

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/` | Retorna o Dashboard da Interface Gráfica (`index.html`). |
| `POST` | `/api/v1/finops/scans/trigger` | Executa uma nova varredura de recursos ociosos sob demanda. |
| `GET` | `/api/v1/finops/reports/latest` | Retorna o último relatório de análise de custos e recursos. |
| `GET` | `/api/v1/finops/resources/idle` | Lista individualmente todos os recursos ociosos identificados. |
| `GET` | `/actuator/prometheus` | Expõe as métricas do sistema e contadores de varredura para o Prometheus. |

