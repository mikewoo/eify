#!/bin/bash

# Jaeger 启动脚本

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示使用说明
show_usage() {
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  start    启动 Jaeger 服务"
    echo "  stop     停止 Jaeger 服务"
    echo "  restart  重启 Jaeger 服务"
    echo "  status   查看 Jaeger 服务状态"
    echo "  logs     查看 Jaeger 日志"
    echo "  ui       打开 Jaeger UI"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 status"
    echo ""
    exit 1
}

# 检查参数
if [ $# -eq 0 ]; then
    show_usage
fi

# 获取脚本所在目录和项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-jaeger.yml"

# 检查 docker-compose 是否安装
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    log_error "docker-compose 未安装，请先安装 Docker Compose"
    exit 1
fi

# 使用 docker compose 还是 docker-compose
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# 命令处理
case "$1" in
    start)
        log_info "启动 Jaeger 服务..."
        $DOCKER_COMPOSE -f "$COMPOSE_FILE" up -d
        log_info "Jaeger 服务启动成功 ✓"
        echo ""
        echo "服务访问地址:"
        echo "  Jaeger UI:     http://localhost:16686"
        echo "  OTLP gRPC:     localhost:4317"
        echo "  OTLP HTTP:     http://localhost:4318"
        echo ""
        echo "管理命令:"
        echo "  查看状态: $0 status"
        echo "  查看日志: $0 logs"
        echo "  打开 UI:  $0 ui"
        echo "  停止服务: $0 stop"
        ;;

    stop)
        log_info "停止 Jaeger 服务..."
        $DOCKER_COMPOSE -f "$COMPOSE_FILE" down
        log_info "Jaeger 服务已停止"
        ;;

    restart)
        log_info "重启 Jaeger 服务..."
        $DOCKER_COMPOSE -f "$COMPOSE_FILE" restart
        log_info "Jaeger 服务重启成功 ✓"
        ;;

    status)
        log_info "Jaeger 服务状态:"
        $DOCKER_COMPOSE -f "$COMPOSE_FILE" ps
        ;;

    logs)
        log_info "Jaeger 服务日志:"
        $DOCKER_COMPOSE -f "$COMPOSE_FILE" logs -f jaeger
        ;;

    ui)
        log_info "打开 Jaeger UI..."
        # 检测操作系统
        if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
            # Windows
            start http://localhost:16686
        elif [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS
            open http://localhost:16686
        else
            # Linux
            xdg-open http://localhost:16686 2>/dev/null || echo "请手动打开: http://localhost:16686"
        fi
        ;;

    *)
        log_error "未知命令: $1"
        show_usage
        ;;
esac
