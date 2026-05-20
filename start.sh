#!/bin/bash

set -e  # 遇到错误立即退出

# ========== 环境检测 ==========
ENV=${1:-dev}
PROJECT_NAME="eify"
BACKEND_PORT=8080
FRONTEND_PORT=5173

# 检测是否在 CI/CD 环境中
is_ci() {
    [ -n "$CI" ] || [ -n "$GITHUB_ACTIONS" ] || [ -n "$JENKINS_URL" ] || [ -n "$GITLAB_CI" ]
}

# ========== 日志函数 ==========
# CI 环境禁用颜色输出
if is_ci; then
    log_info()  { echo "[INFO] $1"; }
    log_warn()  { echo "[WARN] $1"; }
    log_error() { echo "[ERROR] $1"; }
else
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    NC='\033[0m'
    log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
    log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
    log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
fi

# ========== 使用说明 ==========
show_usage() {
    echo "Usage: $0 [ENVIRONMENT]"
    echo ""
    echo "Arguments:"
    echo "  ENVIRONMENT    运行环境: dev (默认) | test | staging | prod"
    echo ""
    echo "Examples:"
    echo "  $0              # 启动开发环境"
    echo "  $0 dev          # 启动开发环境"
    echo "  $0 test         # 启动测试环境"
    echo "  $0 staging      # 启动预发布环境"
    echo "  $0 prod         # 启动生产环境"
    echo ""
    echo "环境变量（生产环境必须设置）:"
    echo "  JWT_SECRET          JWT 签名密钥（openssl rand -base64 32）"
    echo "  CRYPTO_KEK          加密密钥（openssl rand -base64 32）"
    echo "  MYSQL_HOST          数据库主机"
    echo "  MYSQL_PORT          数据库端口"
    echo "  MYSQL_DATABASE      数据库名称"
    echo "  MYSQL_USERNAME      数据库用户名"
    echo "  MYSQL_PASSWORD      数据库密码"
    echo "  REDIS_HOST          Redis 主机"
    echo "  REDIS_PORT          Redis 端口"
    echo "  EMBEDDING_API_KEY   Embedding API 密钥"
    echo "  PGVECTOR_HOST       pgvector 主机"
    echo "  PGVECTOR_PORT       pgvector 端口"
    echo "  PGVECTOR_DATABASE   pgvector 数据库"
    echo "  PGVECTOR_USERNAME   pgvector 用户名"
    echo "  PGVECTOR_PASSWORD   pgvector 密码"
    echo ""
    exit 1
}

if [[ "$ENV" != "dev" && "$ENV" != "test" && "$ENV" != "staging" && "$ENV" != "prod" ]]; then
    log_error "无效的环境参数: $ENV"
    show_usage
fi

# ========== 项目路径 ==========
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# 日志和 PID 文件路径
BACKEND_LOG_FILE="${PROJECT_ROOT}/logs/backend-${ENV}.log"
FRONTEND_LOG_FILE="${PROJECT_ROOT}/logs/frontend-${ENV}.log"
PID_FILE="${PROJECT_ROOT}/logs/${PROJECT_NAME}-${ENV}.pid"
mkdir -p "${PROJECT_ROOT}/logs"

log_info "========== Eify 启动脚本 =========="
log_info "项目根目录: $PROJECT_ROOT"
log_info "运行环境: ${ENV}"
is_ci && log_info "检测到 CI/CD 环境"
echo ""

# ========== 加载环境配置 ==========
if [ "$ENV" = "dev" ]; then
    # 开发环境：从 .env 文件加载配置
    if [ -f "${PROJECT_ROOT}/.env" ]; then
        log_info "加载环境配置: ${PROJECT_ROOT}/.env"
        set -a
        source "${PROJECT_ROOT}/.env"
        set +a
    else
        log_warn ".env 文件不存在，使用默认配置"
    fi
else
    # 生产环境：环境变量必须由 CI/CD 或容器编排系统注入
    log_info "生产环境：使用系统环境变量"
    # 检查必要的环境变量
    if [ -z "$MYSQL_URL" ]; then
        log_error "生产环境必须设置 MYSQL_URL 环境变量"
        exit 1
    fi
fi
echo ""

# ========== 检查依赖 ==========
log_info "检查依赖..."

if ! command -v java &> /dev/null; then
    log_error "java 未安装，请先安装 JDK 21+"
    exit 1
fi

if [ "$ENV" = "dev" ]; then
    if ! command -v mvn &> /dev/null; then
        log_error "mvn 未安装，请先安装 Maven"
        exit 1
    fi
    if ! command -v npm &> /dev/null; then
        log_error "npm 未安装，请先安装 Node.js"
        exit 1
    fi
fi

log_info "依赖检查完成"
echo ""

# ========== 检查服务可用性（仅开发环境） ==========
if [ "$ENV" = "dev" ]; then
    check_service() {
        local host=$1
        local port=$2
        local name=$3
        local required=$4  # true/false

        if command -v nc &> /dev/null; then
            if ! nc -z "$host" "$port" 2>/dev/null; then
                if [ "$required" = "true" ]; then
                    log_error "$name 不可用 (${host}:${port})"
                    exit 1
                else
                    log_warn "$name 不可用 (${host}:${port})，部分功能将不可用"
                fi
            else
                log_info "$name 可用"
            fi
        fi
    }

    MYSQL_HOST="${MYSQL_HOST:-localhost}"
    MYSQL_PORT="${MYSQL_PORT:-3306}"
    REDIS_HOST="${REDIS_HOST:-localhost}"
    REDIS_PORT="${REDIS_PORT:-6379}"

    check_service "$MYSQL_HOST" "$MYSQL_PORT" "MySQL" "true"
    check_service "$REDIS_HOST" "$REDIS_PORT" "Redis" "true"
    check_service "${PGVECTOR_HOST:-localhost}" "${PGVECTOR_PORT:-5432}" "PostgreSQL" "false"
    echo ""
fi

# ========== 停止旧进程 ==========
stop_old_process() {
    if [ -f "$PID_FILE" ]; then
        local old_pid
        old_pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
        if [ -n "$old_pid" ] && ps -p "$old_pid" > /dev/null 2>&1; then
            log_warn "停止旧进程 (PID: $old_pid)"
            if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
                taskkill //F //PID "$old_pid" 2>/dev/null || true
            else
                kill "$old_pid" 2>/dev/null || true
            fi
            sleep 2
        fi
        rm -f "$PID_FILE"
    fi
}

stop_old_process

# 检查端口占用
check_and_kill_port() {
    local port=$1
    local name=$2
    if command -v netstat &> /dev/null; then
        local pid
        pid=$(netstat -ano 2>/dev/null | grep ":$port " | grep "LISTENING" | awk '{print $5}' | head -1 || true)
        if [ -n "$pid" ]; then
            log_warn "停止占用 $name 端口的进程 (PID: $pid)"
            if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
                taskkill //F //PID "$pid" 2>/dev/null || true
            else
                kill "$pid" 2>/dev/null || true
            fi
            sleep 2
        fi
    fi
}

check_and_kill_port $BACKEND_PORT "后端"
[ "$ENV" = "dev" ] && check_and_kill_port $FRONTEND_PORT "前端"
echo ""

# ========== 构建后端 ==========
if [ "$ENV" = "prod" ] || [ "$ENV" = "staging" ] || [ "$ENV" = "test" ]; then
    log_info "构建后端项目（${ENV} 环境）..."
    mvn clean package -DskipTests -q
else
    log_info "构建后端项目（开发环境）..."
    mvn clean install -DskipTests -q
fi

if [ $? -ne 0 ]; then
    log_error "后端构建失败"
    exit 1
fi
log_info "后端构建完成"
echo ""

# ========== 启动后端 ==========
log_info "启动后端服务..."

# 版本号
APP_VERSION=$(grep '<eify.version>' pom.xml 2>/dev/null | sed 's/.*<eify.version>\(.*\)<\/eify.version>.*/\1/' || echo "1.0.0")
SPRING_OPTS="-Dspring.profiles.active=${ENV} -Dapp.version=${APP_VERSION}"

# JVM 配置（可通过环境变量覆盖）
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2048m -XX:MaxMetaspaceSize=256m}"

if [ "$ENV" = "prod" ] || [ "$ENV" = "staging" ] || [ "$ENV" = "test" ]; then
    # K8s 部署环境：使用 JAR 启动
    JAR_FILE=$(ls eify-app/target/${PROJECT_NAME}-app-*-SNAPSHOT.jar 2>/dev/null | head -1)
    if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
        log_error "JAR 文件不存在，请先执行 mvn clean package -DskipTests"
        exit 1
    fi

    # 生产环境：前台运行（适合容器化部署）
    if [ "$FOREGROUND" = "true" ]; then
        log_info "前台模式启动（容器化部署）"
        exec java $JAVA_OPTS $SPRING_OPTS -jar "$JAR_FILE"
    else
        # 后台运行（适合传统部署）
        java $JAVA_OPTS $SPRING_OPTS -jar "$JAR_FILE" > $BACKEND_LOG_FILE 2>&1 &
        BACKEND_PID=$!
        echo $BACKEND_PID > $PID_FILE
        log_info "后端 PID: $BACKEND_PID"
    fi
else
    # 开发环境：使用 mvn spring-boot:run
    mvn spring-boot:run -pl eify-app \
        -Dspring-boot.run.profiles=${ENV} \
        -Dspring-boot.run.jvmArguments="$JAVA_OPTS" \
        > $BACKEND_LOG_FILE 2>&1 &
    BACKEND_PID=$!
    echo $BACKEND_PID > $PID_FILE
    log_info "后端 PID: $BACKEND_PID"
fi

log_info "日志文件: $BACKEND_LOG_FILE"
sleep 5
echo ""

# ========== 等待后端启动 ==========
log_info "等待后端启动..."
MAX_WAIT="${MAX_WAIT:-60}"
WAIT_COUNT=0

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if curl -s http://localhost:${BACKEND_PORT}/api/v1/health > /dev/null 2>&1; then
        log_info "后端启动成功"
        break
    fi
    WAIT_COUNT=$((WAIT_COUNT + 1))
    is_ci || echo -n "."
    sleep 1
done
echo ""

if [ $WAIT_COUNT -eq $MAX_WAIT ]; then
    log_error "后端启动超时（${MAX_WAIT}秒）"
    if [ -f "$BACKEND_LOG_FILE" ]; then
        echo "最后 30 行日志:"
        tail -30 $BACKEND_LOG_FILE
    fi
    exit 1
fi

# ========== 健康检查 ==========
HEALTH_RESPONSE=$(curl -s http://localhost:${BACKEND_PORT}/api/v1/health 2>/dev/null || echo "")
if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"'; then
    log_info "健康检查通过"
else
    log_warn "健康检查异常: $HEALTH_RESPONSE"
fi
echo ""

# ========== 启动前端（仅开发环境） ==========
if [ "$ENV" = "dev" ]; then
    log_info "启动前端开发服务器..."
    cd eify-web
    APP_VERSION=${APP_VERSION} npm run dev > $FRONTEND_LOG_FILE 2>&1 &
    FRONTEND_PID=$!
    echo $FRONTEND_PID >> $PID_FILE
    cd ..
    log_info "前端 PID: $FRONTEND_PID"
    log_info "前端日志: $FRONTEND_LOG_FILE"
    sleep 3
    echo ""
fi

# ========== 启动完成 ==========
log_info "========== Eify 启动完成 =========="
echo ""
echo "环境: ${ENV}"
echo "服务地址:"
echo "  后端 API: http://localhost:${BACKEND_PORT}"
echo "  API 文档: http://localhost:${BACKEND_PORT}/doc.html"
[ "$ENV" = "dev" ] && echo "  前端开发: http://localhost:${FRONTEND_PORT}"
echo ""

# ========== K8s 部署环境：等待后端进程结束 ==========
if ( [ "$ENV" = "prod" ] || [ "$ENV" = "staging" ] || [ "$ENV" = "test" ] ) && [ "$FOREGROUND" != "true" ]; then
    # CI/CD 环境：等待启动后退出，由 CI 系统管理进程
    if is_ci; then
        log_info "CI/CD 环境：启动完成，退出脚本"
        exit 0
    fi

    # 传统部署：等待中断信号
    trap 'log_info "正在停止服务..."; kill $BACKEND_PID 2>/dev/null; wait $BACKEND_PID 2>/dev/null; rm -f $PID_FILE; log_info "服务已停止"; exit 0' INT TERM

    log_info "按 Ctrl+C 停止服务"
    wait $BACKEND_PID
    rm -f $PID_FILE
    exit 0
fi

# ========== 开发环境：保持运行 ==========
if [ "$ENV" = "dev" ]; then
    echo "管理:"
    echo "  停止服务: ./stop.sh ${ENV}"
    echo "  查看日志: tail -f $BACKEND_LOG_FILE"
    echo ""

    trap 'echo ""; log_info "正在停止服务..."; [ -n "$BACKEND_PID" ] && kill $BACKEND_PID 2>/dev/null; [ -n "$FRONTEND_PID" ] && kill $FRONTEND_PID 2>/dev/null; rm -f $PID_FILE; log_info "服务已停止"; exit 0' INT TERM

    log_info "按 Ctrl+C 停止服务"
    wait
fi
