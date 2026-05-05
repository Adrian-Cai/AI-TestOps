
pipeline {
    // 部署任务必须在主节点（Jenkins Master）执行，不要跑到测试节点
    agent { label 'built-in' }

    environment {
        IMAGE_NAME  = "docker.cnb.cool/imacaiy/ai-testops"
        PROJECT_DIR = "/opt/ai-testops"
        ENV_FILE    = "/opt/ai-testops/.env"
        // CNB Docker Token：在 Jenkins → 凭据 中添加 Secret text，ID 填 CNB_DOCKER_TOKEN
        CNB_TOKEN   = credentials('CNB_DOCKER_TOKEN')
    }

    parameters {
        choice(
            name: 'DEPLOY_ENV',
            choices: ['production'],
            description: '部署环境'
        )
        string(
            name: 'IMAGE_TAG',
            defaultValue: 'latest',
            description: '镜像标签（留空使用 latest，或输入 CNB_COMMIT_SHORT）'
        )
        booleanParam(
            name: 'FORCE_PULL',
            defaultValue: true,
            description: '强制拉取最新镜像'
        )
        booleanParam(
            name: 'BACKUP',
            defaultValue: true,
            description: '部署前备份当前版本'
        )
    }

    options {
        buildDiscarder(logRotator(
            numToKeepStr:         '5',
            artifactNumToKeepStr: '3',
            daysToKeepStr:        '30',
            artifactDaysToKeepStr:'7'
        ))
        timeout(time: 15, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    triggers {
        pollSCM('H/5 * * * *')
    }

    stages {
        stage('准备') {
            steps {
                script {
                    echo "========================================"
                    echo "部署信息"
                    echo "========================================"
                    echo "环境: ${params.DEPLOY_ENV}"
                    echo "镜像: ${IMAGE_NAME}:${params.IMAGE_TAG}"
                    echo "强制拉取: ${params.FORCE_PULL}"
                    echo "备份: ${params.BACKUP}"
                    echo "========================================"
                    echo ""

                    if (!params.IMAGE_TAG?.trim()) {
                        env.IMAGE_TAG = 'latest'
                    } else {
                        env.IMAGE_TAG = params.IMAGE_TAG
                    }

                    // 检测 docker compose 命令格式（兼容新旧 Docker 版本）
                    env.COMPOSE_CMD = sh(
                        script: '''
                            if docker compose version &>/dev/null 2>&1; then
                                echo "docker compose"
                            elif command -v docker-compose &>/dev/null; then
                                echo "docker-compose"
                            else
                                echo "ERROR: 未找到 docker-compose，请安装 docker-compose 插件" >&2
                                exit 1
                            fi
                        ''',
                        returnStdout: true
                    ).trim()

                    echo "Docker Compose 命令: ${env.COMPOSE_CMD}"
                }
            }
        }

        stage('检查环境') {
            steps {
                script {
                    echo "检查服务器环境..."
                    sh """
                        docker --version
                        ${COMPOSE_CMD} --version
                    """
                    sh """
                        if [ ! -d "${PROJECT_DIR}" ]; then
                            echo "警告: 项目目录不存在: ${PROJECT_DIR}，将在下一步自动创建"
                        fi
                    """
                    echo "环境检查完成"
                }
            }
        }

        stage('初始化部署目录') {
            steps {
                script {
                    echo "确保部署目录和配置文件存在..."
                    sh """
                        mkdir -p ${PROJECT_DIR}/{logs,data}

                        # 若 .env 不存在则从仓库 .env.example 生成初始配置
                        if [ ! -f "${ENV_FILE}" ]; then
                            echo "[INFO] ${ENV_FILE} 不存在，从仓库 .env.example 生成初始配置..."
                            if [ -f "\$WORKSPACE/.env.example" ]; then
                                cp "\$WORKSPACE/.env.example" "${ENV_FILE}"
                                echo "[WARN] 已复制 .env.example → ${ENV_FILE}，请登录服务器补全真实配置后重新部署"
                            else
                                touch "${ENV_FILE}"
                                echo "[WARN] 已创建空 ${ENV_FILE}，请登录服务器补全配置后重新部署"
                            fi
                            chmod 600 "${ENV_FILE}"
                        fi

                        # 确保 .env 中包含 HOST_PORT（旧版本 .env 可能没有此字段）
                        if ! grep -q '^HOST_PORT=' "${ENV_FILE}" 2>/dev/null; then
                            echo "[INFO] 补充 HOST_PORT=18888 到 ${ENV_FILE}"
                            echo "HOST_PORT=18888" >> "${ENV_FILE}"
                        fi

                        # 使用固定端口 18888
                        HOST_PORT=18888

                        # 注意: 端口占用检查移到"停止旧容器"阶段，避免重部署时误判正在运行的服务
                        echo "[INFO] 使用宿主机端口: \$HOST_PORT（端口可用性将在停止旧容器阶段验证）"

                        # 覆写 docker-compose.yml
                        cat > ${PROJECT_DIR}/docker-compose.yml << COMPOSE_EOF
services:
  ai-testops:
    image: docker.cnb.cool/imacaiy/ai-testops:latest
    container_name: ai-testops
    restart: unless-stopped
    ports:
      - "\$HOST_PORT:8080"
    env_file:
      - /opt/ai-testops/.env
    volumes:
      - ./data:/app/data
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
COMPOSE_EOF
                        echo "[INFO] docker-compose.yml 已生成（port: \$HOST_PORT）"
                    """
                    echo "目录初始化完成"
                }
            }
        }

        stage('登录 CNB 制品库') {
            steps {
                script {
                    echo "登录 CNB Docker 制品库..."
                    sh 'echo "$CNB_TOKEN" | docker login docker.cnb.cool -u cnb --password-stdin'
                    echo "登录成功"
                }
            }
        }

        stage('拉取镜像') {
            steps {
                script {
                    def pullCmd = params.FORCE_PULL ?
                        "docker pull ${IMAGE_NAME}:${env.IMAGE_TAG}" :
                        "docker pull ${IMAGE_NAME}:${env.IMAGE_TAG} || echo '镜像已存在，跳过拉取'"

                    echo "拉取镜像: ${IMAGE_NAME}:${env.IMAGE_TAG}"
                    sh "${pullCmd}"

                    sh """
                        docker images ${IMAGE_NAME}:${env.IMAGE_TAG}
                    """
                }
            }
        }

        stage('备份当前版本') {
            when {
                expression { params.BACKUP == true }
            }
            steps {
                script {
                    echo "备份当前版本..."
                    sh """
                        BACKUP_TAG="backup-\$(date +%Y%m%d-%H%M%S)"
                        CURRENT_IMAGE=\$(docker inspect --format='{{.Config.Image}}' ai-testops 2>/dev/null || echo "")

                        if [ -n "\$CURRENT_IMAGE" ]; then
                            docker tag "\$CURRENT_IMAGE" ${IMAGE_NAME}:\$BACKUP_TAG || true
                            echo "备份标签: \$BACKUP_TAG (来自当前运行镜像: \$CURRENT_IMAGE)"
                        else
                            echo "未找到运行中的容器，尝试备份 latest 标签..."
                            docker tag ${IMAGE_NAME}:latest ${IMAGE_NAME}:\$BACKUP_TAG || true
                            echo "备份标签: \$BACKUP_TAG (来自 latest)"
                        fi
                    """
                }
            }
        }

        stage('停止旧容器') {
            steps {
                script {
                    echo "停止旧容器并释放端口..."
                    sh """
                        echo "1) 停止 docker compose 管理的服务..."
                        cd ${PROJECT_DIR}
                        ${COMPOSE_CMD} down --remove-orphans || true

                        echo "2) 强制删除可能残留的同名容器..."
                        docker rm -f ai-testops 2>/dev/null || true

                        # 使用固定端口
                        HOST_PORT=18888

                        echo "3) 检查宿主机端口 \$HOST_PORT..."

                        # 检查是否有非 Docker 进程占用此端口
                        PORT_PID=\$(ss -tlnp | grep ":\$HOST_PORT " | sed -n 's/.*pid=\\([0-9]*\\).*/\\1/p' 2>/dev/null || true)
                        if [ -n "\$PORT_PID" ]; then
                            echo "[ERROR] 非 Docker 进程 PID=\$PORT_PID 占用宿主机端口 \$HOST_PORT，请手动处理后重试"
                            exit 1
                        else
                            echo "端口 \$HOST_PORT 已空闲，可以绑定"
                        fi

                        echo "端口检查完成"
                    """
                }
            }
        }

        stage('更新配置') {
            steps {
                script {
                    echo "更新 docker-compose.yml 镜像标签..."

                    def targetImage = params.IMAGE_TAG?.trim() ? params.IMAGE_TAG : 'latest'

                    sh """
                        cd ${PROJECT_DIR}

                        cp docker-compose.yml docker-compose.yml.backup

                        sed -i "s|${IMAGE_NAME}:latest|${IMAGE_NAME}:${targetImage}|g" docker-compose.yml

                        echo "配置更新完成"
                    """
                }
            }
        }

        stage('启动新容器') {
            steps {
                script {
                    echo "启动新容器..."
                    sh """
                        cd ${PROJECT_DIR}
                        ${COMPOSE_CMD} up -d

                        ${COMPOSE_CMD} ps
                    """
                }
            }
        }

        stage('健康检查') {
            steps {
                script {
                    echo "执行健康检查..."
                    def host_port = "18888"
                    echo "健康检查地址: http://localhost:${host_port}/"

                    retry(10) {
                        sleep 5
                        sh """
                            curl -s -o /dev/null -w "%{http_code}" --max-time 10 http://localhost:${host_port}/ | grep -qE '^[23]' || exit 1
                        """
                    }

                    echo "健康检查通过"
                }
            }
        }

        stage('验证部署') {
            steps {
                script {
                    echo "验证部署..."
                    sh """
                        docker ps --filter "name=ai-testops"

                        docker logs --tail=20 ai-testops
                    """
                }
            }
        }

        stage('清理') {
            steps {
                script {
                    echo "清理未使用的资源..."
                    sh """
                        docker image prune -f

                        docker container prune -f

                        docker builder prune -f --filter until=24h || true

                        docker images --format '{{.Repository}}:{{.Tag}}' \
                            | grep 'backup-' \
                            | sort -r \
                            | tail -n +4 \
                            | xargs -r docker rmi -f || true

                        rm -f ${PROJECT_DIR}/docker-compose.yml.backup || true

                        echo "Docker 磁盘占用情况:"
                        docker system df

                        echo "清理完成"
                    """

                    sh 'docker logout docker.cnb.cool || true'
                    cleanWs()
                }
            }
        }
    }

    post {
        success {
            script {
                // 清理备份文件（部署成功不再需要）
                sh """
                    rm -f ${PROJECT_DIR}/docker-compose.yml.backup 2>/dev/null || true
                """

                def host_port = "18888"
                def domain = sh(
                    script: "grep '^DOMAIN=' ${ENV_FILE} 2>/dev/null | cut -d'=' -f2- || echo ''",
                    returnStdout: true
                ).trim()
                def accessUrl = domain ? "http://\${domain}" : "http://\${SERVER_IP}:${host_port}"

                echo ""
                echo "========================================"
                echo "部署成功！"
                echo "========================================"
                echo ""
                echo "访问地址: ${accessUrl}"
                echo "容器状态: docker ps | grep ai-testops"
                echo "查看日志: cd ${PROJECT_DIR} && ${COMPOSE_CMD} logs -f"
                echo ""
                echo "========================================"
            }
        }

        failure {
            script {
                echo ""
                echo "========================================"
                echo "部署失败"
                echo "========================================"
                echo ""

                sh """
                    cd ${PROJECT_DIR} 2>/dev/null || true
                    ${COMPOSE_CMD} logs --tail=100 2>/dev/null || true
                """

                echo ""
                echo "尝试回滚到之前的版本..."
                sh """
                    if [ -f ${PROJECT_DIR}/docker-compose.yml.backup ]; then
                        cd ${PROJECT_DIR}
                        mv docker-compose.yml.backup docker-compose.yml
                        ${COMPOSE_CMD} down --remove-orphans || true
                        docker rm -f ai-testops 2>/dev/null || true
                        ${COMPOSE_CMD} up -d || true
                        echo "回滚完成"
                        # 清理备份文件
                        rm -f docker-compose.yml.backup 2>/dev/null || true
                    else
                        echo "未找到备份配置，跳过回滚"
                    fi
                """
            }
        }

        always {
            script {
                sh """
                    docker logout docker.cnb.cool 2>/dev/null || true
                """
                cleanWs()
            }
        }
    }
}
