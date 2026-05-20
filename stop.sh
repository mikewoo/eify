#!/bin/bash

set -e

# ========== 环境检测 ==========
ENV=${1:-dev}
PROJECT_NAME="eify"
BACKEND_PORT=8080
FRONTEND_PORT=5173

# 获取项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="${PROJECT_ROOT}/logs/${PROJECT_NAME}-${ENV}.pid"

# 检测是否在 CI/CD 环境中
is_ci() {
    [ -n "$CI" ] || [ -n "$GITHUB_ACTIONS" ] || [ -n "$JENKINS_URL" ] || [ -n "$GITLAB_CI" ]
}

# ========== 日志函数 ==========
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
    echo "Usage: $0 [ENVIRONMENT] [OPTIONS]"
    echo ""
    echo "Arguments:"
    echo "  ENVIRONMENT    运行环境: dev (默认) | test | staging | prod"
    echo ""
    echo "Options:"
    echo "  --clean-logs   同时清理日志文件"
    echo ""
    echo "Examples:"
    echo "  $0              # 停止开发环境"
    echo "  $0 test         # 停止测试环境"
    echo "  $0 staging      # 停止预发布环境"
    echo "  $0 prod         # 停止生产环境"
    echo "  $0 dev --clean-logs  # 停止并清理日志"
    echo ""
    exit 1
}

if [[ "$ENV" != "dev" && "$ENV" != "test" && "$ENV" != "staging" && "$ENV" != "prod" ]]; then
    log_error "无效的环境参数: $ENV"
    show_usage
fi

log_info "========== Eify 停止脚本 =========="
log_info "运行环境: ${ENV}"
echo ""

STOPPED_COUNT=0

# ========== Docker Compose 停止 ==========
stop_docker_compose() {
    local compose_file=$1
    local name=$2
    if command -v docker &> /dev/null && [ -f "$compose_file" ]; then
        if docker compose -f "$compose_file" ps 2>/dev/null | grep -q 'Up'; then
            log_info "停止 Docker Compose 服务: $name"
            docker compose -f "$compose_file" down 2>/dev/null || true
            STOPPED_COUNT=$((STOPPED_COUNT + 1))
        fi
    fi
}

DEPLOY_DIR="${PROJECT_ROOT}/deploy/infra/deploy"
stop_docker_compose "${DEPLOY_DIR}/docker-compose.yml" "主服务"
stop_docker_compose "${DEPLOY_DIR}/docker-compose-logging.yml" "日志栈"
echo ""

# ========== 停止 PID 文件中的进程 ==========
if [ -f "$PID_FILE" ]; then
    log_info "从 PID 文件停止进程..."
    PIDS=$(cat "$PID_FILE" 2>/dev/null || echo "")
    if [ -n "$PIDS" ]; then
        for PID in $PIDS; do
            if ps -p "$PID" > /dev/null 2>&1; then
                log_info "  停止进程 (PID: $PID)"
                if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
                    taskkill //F //PID "$PID" 2>/dev/null || true
                else
                    # 先尝试优雅停止，等待 5 秒后强制停止
                    kill "$PID" 2>/dev/null || true
                    sleep 2
                    if ps -p "$PID" > /dev/null 2>&1; then
                        kill -9 "$PID" 2>/dev/null || true
                    fi
                fi
                STOPPED_COUNT=$((STOPPED_COUNT + 1))
                sleep 1
            else
                log_warn "  进程不存在 (PID: $PID)"
            fi
        done
    fi
    rm -f "$PID_FILE"
    log_info "PID 文件已删除"
else
    log_warn "PID 文件不存在: $PID_FILE"
fi
echo ""

# ========== 通过端口停止进程 ==========
stop_by_port() {
    local port=$1
    local name=$2

    if command -v netstat &> /dev/null; then
        local PIDS
        # netstat -ano: PID 在最后一列（兼容 Windows 和 Linux）
        PIDS=$(netstat -ano 2>/dev/null | grep ":$port " | grep "LISTENING" | awk '{print $NF}' || true)
        if [ -n "$PIDS" ]; then
            log_info "停止占用 $name 端口 ($port) 的进程..."
            for PID in $PIDS; do
                if ps -p "$PID" > /dev/null 2>&1; then
                    log_info "  停止 $name 进程 (PID: $PID, Port: $port)"
                    if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
                        taskkill //F //PID "$PID" 2>/dev/null || true
                    else
                        kill "$PID" 2>/dev/null || true
                        sleep 2
                        if ps -p "$PID" > /dev/null 2>&1; then
                            kill -9 "$PID" 2>/dev/null || true
                        fi
                    fi
                    STOPPED_COUNT=$((STOPPED_COUNT + 1))
                    sleep 1
                fi
            done
        fi
    elif command -v lsof &> /dev/null; then
        local PIDS
        PIDS=$(lsof -ti:$port 2>/dev/null || true)
        if [ -n "$PIDS" ]; then
            log_info "停止占用 $name 端口 ($port) 的进程..."
            for PID in $PIDS; do
                if ps -p "$PID" > /dev/null 2>&1; then
                    log_info "  停止 $name 进程 (PID: $PID, Port: $port)"
                    kill "$PID" 2>/dev/null || true
                    STOPPED_COUNT=$((STOPPED_COUNT + 1))
                    sleep 1
                fi
            done
        fi
    fi
}

stop_by_port $BACKEND_PORT "后端"
[ "$ENV" = "dev" ] && stop_by_port $FRONTEND_PORT "前端"
echo ""

# ========== 清理日志文件（可选） ==========
if [ "$2" = "--clean-logs" ]; then
    log_info "清理日志文件..."
    rm -f "${PROJECT_ROOT}/logs/backend-${ENV}.log"
    rm -f "${PROJECT_ROOT}/logs/frontend-${ENV}.log"
    log_info "日志文件已清理"
    echo ""
fi

# ========== 完成 ==========
if [ $STOPPED_COUNT -gt 0 ]; then
    log_info "========== 停止完成 =========="
    log_info "已停止 $STOPPED_COUNT 个进程"
else
    log_warn "没有找到需要停止的进程"
fi
echo ""

# ========== 验证端口是否已释放 ==========
verify_port_released() {
    local port=$1
    local name=$2

    if command -v netstat &> /dev/null; then
        if netstat -ano 2>/dev/null | grep ":$port " | grep "LISTENING" > /dev/null; then
            log_warn "$name 端口 ($port) 仍被占用"
        else
            log_info "$name 端口 ($port) 已释放"
        fi
    fi
}

log_info "验证端口状态..."
verify_port_released $BACKEND_PORT "后端"
[ "$ENV" = "dev" ] && verify_port_released $FRONTEND_PORT "前端"
echo ""
