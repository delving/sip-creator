pipeline {
    agent { label 'maven' }
    environment {
        PATH = "/usr/local/maven/bin:$PATH"
    }
    stages {
        stage('Build') {
            steps {
                echo 'Building'
                gerritReview labels: [Verified: 0]
                sh '[ -x _scripts/prepare_build.sh ] && _scripts/prepare_build.sh'
                sh '[ -x ~/prepare_sip-creator.sh ] && ~/prepare_sip-creator.sh'
                sh 'mvn clean install -Dmaven.test.skip'
            }
        }
        stage('Test') {
            steps {
                echo 'Testing'
                sh 'mvn test'
            }
        }
        stage('Deploy') {
            steps {
                echo 'Deploying'
                sh '[ -x ~/deploy_sip-creator.sh ] && ~/deploy_sip-creator.sh'
            }
        }
    }
    post {
        success {
            gerritReview labels: [Verified: 1]
        }
        unstable { gerritReview labels: [Verified: 0], message: 'Build is unstable' }
        failure {
            gerritReview labels: [Verified: -1]
        }
        always {
            echo 'Archiving'
            archiveArtifacts artifacts: '*/target/*.jar', fingerprint: true
            //echo 'Cleaning workspace'
            //cleanWs()
        }
    }
}
