pipeline { 

    agent any 
    
    options{ 
        timeout(time: 15, unit: 'MINUTES')
        timestamps()  
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

    }

    post{ 

        always{
            // Publica no Jenkins os relatórios XML dos testes Maven - para visualizar testes aprovados, falhos e etc
            junit 'target/surefire-reports/*.xml'

            // Guarda os arquivos .jar gerados no build para download posterior (artefatos de execução)
            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            
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