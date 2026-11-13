pipeline { 

    agent any 
    
    environment{ 
        IMAGE_NAME = 'gabcirino/cloudSaver' 
        IMAGE_TAG = "${env.BUILD_NUMBER}" 
    }

    tools{ 
        maven 'Maven-3.9'
    }

    options{ 
        timeout(time: 15, unit: 'MINUTES')
        timestamps()  
    }

    // Só funciona com tunnel do Cloudflare ativo! 
    // cloudflared tunnel --url http://localhost:8081
    triggers{ 
        githubPush() 
    }

    stages { 

        stage ('Build'){ 
            steps{
                echo 'Compilando o código...'
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Test'){ 
            steps{ 
                echo 'Executando testes...'
                sh 'mvn test' 
            }
        }

        stage('Build Docker Image'){ 
            steps{
                echo 'Gerando imagem Docker...'
                sh """"
                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                """ 
            }
        }

        stage('Trivy Verification'){ 
            steps{ 
                echo 'Fazendo verificação de vulnerabilidades com Trivy...'
                sh """
                    export PATH="$HOME/.local/bin:$PATH"

                    trivy image --severity HIGH,CRITICAL --exit-code 1 --format table seu-usuario/sua-aplicacao:${env.BUILD_NUMBER}
                """ 
            }
        }
    }

    post{ 

        always{
            // Publica no Jenkins os relatórios XML dos testes Maven - para visualizar testes aprovados, falhos e etc
            junit 'target/surefire-reports/*.xml'

            // Guarda os arquivos .jar gerados no build para download posterior (artefatos de execução)
            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true

            // Remove a imagem Docker após a execução do pipeline - isso ajuda a economizar espaço em disco 
            sh "docker rmi ${IMAGE_NAME}:${IMAGE_TAG} || true" 
            
            // Limpa o workspace do Jenkins após a execução do pipeline - isso ajuda a economizar espaço em disco 
            cleanWs() 
        }

        success{ 
            echo 'Pipeline executada com sucesso!' 
        }
        failure{ 
            echo 'Pipeline falhou!'
        }
    }
}