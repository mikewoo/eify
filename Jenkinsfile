pipeline {
    agent any

    parameters {
        choice(
            name: 'ENVIRONMENT',
            choices: ['auto', 'dev', 'test', 'staging', 'prod'],
            description: '部署环境（auto = 根据分支/Tag 自动选择；手动触发 prod 部署时请指定 Tag）'
        )
        string(name: 'TAG_NAME', defaultValue: '', description: 'Git Tag 名称（手动触发生产部署时指定，如 v1.0.0.20260520103045.42）')
        string(name: 'MYSQL_HOST', defaultValue: '', description: 'MySQL 主机地址（留空使用 ConfigMap 默认值）')
        string(name: 'REDIS_HOST', defaultValue: '', description: 'Redis 主机地址')
        string(name: 'PGVECTOR_HOST', defaultValue: '', description: 'PostgreSQL 主机地址')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '跳过测试阶段')
    }

    environment {
        // 镜像仓库
        REGISTRY = 'swr.cn-south-1.myhuaweicloud.com/eify'
        BACKEND_IMAGE = "${REGISTRY}/eify-backend"
        FRONTEND_IMAGE = "${REGISTRY}/eify-frontend"

        // 有效的 Git Tag（Webhook 触发或手动指定）
        EFFECTIVE_TAG = env.TAG_NAME ?: params.TAG_NAME

        // 自动映射分支/Tag → 部署环境
        // Git Tag → prod；保护分支按命名规则映射；main 分支不部署
        DEPLOY_ENV = params.ENVIRONMENT != 'auto' ? params.ENVIRONMENT :
                     EFFECTIVE_TAG ? 'prod' :
                     env.BRANCH_NAME == 'alpha' ? 'dev' :
                     env.BRANCH_NAME == 'beta' ? 'test' :
                     env.BRANCH_NAME == 'rc' ? 'staging' :
                     null

        // K8s 命名空间：生产用 eify，其他用 eify-{env}
        K8S_NAMESPACE = DEPLOY_ENV == 'prod' ? 'eify' : "eify-${DEPLOY_ENV}"

        // 镜像标签
        // prod：使用 Git Tag 名（手动或 Tag Webhook 触发必须有 Tag）
        // 其他：{env}-{BUILD}-{commit}
        IMAGE_TAG = DEPLOY_ENV == 'prod' ?
                    (EFFECTIVE_TAG ?: "prod-${BUILD_NUMBER}-${env.GIT_COMMIT?.take(8) ?: 'unknown'}") :
                    "${DEPLOY_ENV}-${BUILD_NUMBER}-${env.GIT_COMMIT?.take(8) ?: 'unknown'}"

        // 前端展示版本号：Tag 名优先，否则用部署环境+构建号，最后回退到分支名
        APP_VERSION = EFFECTIVE_TAG ?: (DEPLOY_ENV ? "${DEPLOY_ENV}-${BUILD_NUMBER}" : env.BRANCH_NAME)

        // 是否需要部署（main 分支仅构建+测试不部署；feature/hotfix 也不部署）
        SHOULD_DEPLOY = DEPLOY_ENV != null ? 'true' : 'false'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        // ========== Stage 1: 构建 ==========
        stage('Build') {
            steps {
                echo "Branch: ${env.BRANCH_NAME}, Tag: ${EFFECTIVE_TAG ?: 'N/A'}"
                echo "Deploy target: ${DEPLOY_ENV ?: (env.BRANCH_NAME == 'main' ? 'none (main branch, deploy via Git Tag manually)' : 'none (feature/hotfix branch)')}"
                echo "Image tag: ${IMAGE_TAG}"

                echo "Building backend..."
                sh 'mvn clean package -DskipTests -q'

                echo "Building frontend (APP_VERSION=${APP_VERSION})..."
                sh "cd eify-web && npm ci --silent && APP_VERSION=${APP_VERSION} npm run build"
            }
        }

        // ========== Stage 2: 测试 ==========
        stage('Test') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                echo "Running tests..."
                sh 'mvn test -q'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        // ========== Stage 3: 构建并推送 Docker 镜像 ==========
        stage('Docker Build & Push') {
            when {
                expression { SHOULD_DEPLOY == 'true' }
            }
            steps {
                echo "Building and pushing images with tag: ${IMAGE_TAG}"

                withCredentials([usernamePassword(
                    credentialsId: 'swr-credentials',
                    usernameVariable: 'SWR_USER',
                    passwordVariable: 'SWR_PASS'
                )]) {
                    sh "docker login -u \${SWR_USER} -p \${SWR_PASS} swr.cn-south-1.myhuaweicloud.com"
                }

                // 后端镜像
                sh "docker build -t ${BACKEND_IMAGE}:${IMAGE_TAG} -t ${BACKEND_IMAGE}:latest -f deploy/Dockerfile ."
                sh "docker push ${BACKEND_IMAGE}:${IMAGE_TAG}"
                sh "docker push ${BACKEND_IMAGE}:latest"

                // 前端镜像
                sh "docker build -t ${FRONTEND_IMAGE}:${IMAGE_TAG} -t ${FRONTEND_IMAGE}:latest -f deploy/Dockerfile.web ."
                sh "docker push ${FRONTEND_IMAGE}:${IMAGE_TAG}"
                sh "docker push ${FRONTEND_IMAGE}:latest"
            }
        }

        // ========== Stage 4: 部署到 K8s ==========
        stage('Deploy to K8s') {
            when {
                expression { SHOULD_DEPLOY == 'true' }
            }
            steps {
                echo "Deploying to K8s namespace: ${K8S_NAMESPACE} (env: ${DEPLOY_ENV})"

                // 首次部署：创建命名空间
                sh "kubectl apply -f deploy/k8s/namespace.yaml || true"

                // 更新 ConfigMap（替换占位符）
                sh """
                    sed -i 's/<MYSQL_HOST>/${params.MYSQL_HOST ?: 'localhost'}/g' deploy/k8s/configmap.yaml
                    sed -i 's/<REDIS_HOST>/${params.REDIS_HOST ?: 'localhost'}/g' deploy/k8s/configmap.yaml
                    sed -i 's/<PGVECTOR_HOST>/${params.PGVECTOR_HOST ?: 'localhost'}/g' deploy/k8s/configmap.yaml
                    sed -i 's/SPRING_PROFILES_ACTIVE: "prod"/SPRING_PROFILES_ACTIVE: "${DEPLOY_ENV}"/g' deploy/k8s/configmap.yaml
                """
                sh "kubectl apply -f deploy/k8s/configmap.yaml"

                // Secret：使用 Jenkins credentials 动态创建
                withCredentials([
                    string(credentialsId: 'mysql-password', variable: 'MYSQL_PASS'),
                    string(credentialsId: 'redis-password', variable: 'REDIS_PASS'),
                    string(credentialsId: 'jwt-secret', variable: 'JWT_SECRET'),
                    string(credentialsId: 'crypto-kek', variable: 'CRYPTO_KEK'),
                    string(credentialsId: 'pgvector-password', variable: 'PGVECTOR_PASS'),
                    string(credentialsId: 'embedding-api-key', variable: 'EMBEDDING_API_KEY')
                ]) {
                    sh """
                        kubectl create secret generic eify-secret \
                            --from-literal=MYSQL_USERNAME=root \
                            --from-literal=MYSQL_PASSWORD=\${MYSQL_PASS} \
                            --from-literal=REDIS_PASSWORD=\${REDIS_PASS} \
                            --from-literal=JWT_SECRET=\${JWT_SECRET} \
                            --from-literal=CRYPTO_KEK=\${CRYPTO_KEK} \
                            --from-literal=PGVECTOR_PASSWORD=\${PGVECTOR_PASS} \
                            --from-literal=EMBEDDING_API_KEY=\${EMBEDDING_API_KEY} \
                            -n ${K8S_NAMESPACE} \
                            --dry-run=client -o yaml | kubectl apply -f -
                    """
                }

                // 更新镜像版本
                sh "kubectl set image deployment/eify-backend eify-backend=${BACKEND_IMAGE}:${IMAGE_TAG} -n ${K8S_NAMESPACE}"
                sh "kubectl set image deployment/eify-frontend eify-frontend=${FRONTEND_IMAGE}:${IMAGE_TAG} -n ${K8S_NAMESPACE}"

                // 等待滚动更新完成
                sh "kubectl rollout status deployment/eify-backend -n ${K8S_NAMESPACE} --timeout=180s"
                sh "kubectl rollout status deployment/eify-frontend -n ${K8S_NAMESPACE} --timeout=120s"
            }
        }

        // ========== Stage 5: 健康检查 ==========
        stage('Health Check') {
            when {
                expression { SHOULD_DEPLOY == 'true' }
            }
            steps {
                echo "Verifying deployment..."
                sh '''
                    sleep 10
                    BACKEND_POD=$(kubectl get pods -n ${K8S_NAMESPACE} -l app=eify-backend -o jsonpath='{.items[0].metadata.name}')
                    kubectl exec -n ${K8S_NAMESPACE} ${BACKEND_POD} -- wget -q --spider http://localhost:8080/api/v1/health
                '''
            }
        }

    }

    post {
        success {
            script {
                if (SHOULD_DEPLOY == 'true') {
                    echo "Deployment to ${DEPLOY_ENV} (${K8S_NAMESPACE}) successful! Image tag: ${IMAGE_TAG}"
                } else if (env.BRANCH_NAME == 'main') {
                    echo "Build & test passed. Deploy via Git Tag manually."
                } else {
                    echo "Build & test passed. No deploy (feature/hotfix branch)."
                }
            }
        }
        failure {
            script {
                if (SHOULD_DEPLOY == 'true') {
                    echo "Deployment failed! Rolling back..."
                    sh "kubectl rollout undo deployment/eify-backend -n ${K8S_NAMESPACE} || true"
                    sh "kubectl rollout undo deployment/eify-frontend -n ${K8S_NAMESPACE} || true"
                }
            }
        }
        always {
            // 清理本地 Docker 镜像
            sh "docker rmi ${BACKEND_IMAGE}:${IMAGE_TAG} ${FRONTEND_IMAGE}:${IMAGE_TAG} 2>/dev/null || true"
        }
    }
}
