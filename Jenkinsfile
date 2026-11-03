pipeline { 

    agent any 
    

    stages { 

        stage('Checkout'){ 
            
            steps{ 
                echo 'Baixando código do repositório...' 
                checkout scm 

            }
        }

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
        success{ 
            echo 'Pipeline executada com sucesso!' 
        }
        failure{ 
            echo 'Pipeline falhou!'
        }
    }
}