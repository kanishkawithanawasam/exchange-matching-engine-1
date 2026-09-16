pipeline {
    agent any
    options { timeout(time: 10, unit: 'MINUTES') }
    parameters { booleanParam(name: 'RUN_PERFORMANCE', defaultValue: false, description: 'Run only on a quiet, dedicated performance agent with Java 17+ installed') }
    stages {
        stage('Verify') { steps { sh './mvnw -B verify' } }
        stage('Recovery demo') { steps { sh 'java -jar target/exchange.jar demo' } }
        stage('Performance') {
            when { expression { params.RUN_PERFORMANCE } }
            agent { label 'performance' }
            steps { sh './mvnw -B verify -Pbenchmarks' }
        }
    }
    post {
        always { junit 'target/surefire-reports/*.xml,target/failsafe-reports/TEST-*.xml' }
        success { archiveArtifacts artifacts: 'target/exchange.jar,target/benchmark-results/*', allowEmptyArchive: true }
    }
}
