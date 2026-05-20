#!/bin/bash
# Eify 日志监控系统本地部署脚本
# 适用于本地开发环境（或任何支持 Docker 的 Linux）
# 版本：2026.5 (2026年5月)
#
# 注意：此脚本部署日志监控栈（ClickHouse + Vector + Grafana + Prometheus）
#       不含应用服务本身，应用服务通过 K8s (CCE Turbo) 部署

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Eify 日志监控系统一键部署脚本${NC}"
echo -e "${GREEN}  版本：2026.5${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 软件版本（与 docker-compose-logging.yml 保持一致）
CLICKHOUSE_VERSION="24.11.1.2988"
CLICKVISUAL_VERSION="latest"
VECTOR_VERSION="0.43.1"
GRAFANA_VERSION="11.4.0"
PROMETHEUS_VERSION="v2.57.0"

echo -e "${YELLOW}软件版本清单：${NC}"
echo "  ClickHouse:     ${CLICKHOUSE_VERSION}"
echo "  ClickVisual:    ${CLICKVISUAL_VERSION}"
echo "  Vector:         ${VECTOR_VERSION}-alpine"
echo "  Grafana:        ${GRAFANA_VERSION}"
echo "  Prometheus:     ${PROMETHEUS_VERSION}"
echo ""

# 确定脚本和项目目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# 检查 Docker
echo -e "${YELLOW}[1/5] 检查 Docker 环境...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker 未安装${NC}"
    echo "请先安装 Docker: curl -fsSL https://get.docker.com | bash"
    exit 1
fi
echo -e "${GREEN}✓ Docker 已安装: $(docker --version)${NC}"

# Docker Compose 子命令 (新版本用 "docker compose"，旧版用 "docker-compose")
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}错误: Docker Compose 未安装${NC}"
        exit 1
    fi
fi
echo -e "${GREEN}✓ Docker Compose 已可用${NC}"
echo ""

# 创建部署目录
echo -e "${YELLOW}[2/5] 创建部署目录...${NC}"
DEPLOY_DIR="/opt/eify/deploy"
sudo mkdir -p "$DEPLOY_DIR"
sudo mkdir -p "$DEPLOY_DIR"/{clickhouse/{data,logs,conf.d},clickvisual/{data,conf},grafana/data,prometheus/data,vector}
sudo chmod -R 777 "$DEPLOY_DIR"/clickhouse/data "$DEPLOY_DIR"/clickvisual/data "$DEPLOY_DIR"/grafana/data "$DEPLOY_DIR"/prometheus/data
echo -e "${GREEN}✓ 部署目录已创建: $DEPLOY_DIR${NC}"
echo ""

# 复制配置文件
echo -e "${YELLOW}[3/5] 复制配置文件...${NC}"

# 使用项目中的 docker-compose-logging.yml 作为部署模板
if [ -f "$SCRIPT_DIR/docker-compose-logging.yml" ]; then
    cp "$SCRIPT_DIR/docker-compose-logging.yml" "$DEPLOY_DIR/docker-compose.yml"
    echo -e "${GREEN}✓ docker-compose.yml 已复制${NC}"
else
    echo -e "${RED}错误: 未找到 docker-compose-logging.yml${NC}"
    exit 1
fi

# ClickHouse 初始化 SQL
	# 资源文件在 infra/ 目录（脚本在 infra/deploy/ 子目录）
	RESOURCES_DIR="$SCRIPT_DIR/.."
if [ -f "$RESOURCES_DIR/clickhouse/init.sql" ]; then
    cp "$RESOURCES_DIR/clickhouse/init.sql" "$DEPLOY_DIR/clickhouse/init.sql"
    echo -e "${GREEN}✓ clickhouse/init.sql 已复制${NC}"
else
    echo -e "${YELLOW}⚠ clickhouse/init.sql 未找到，跳过${NC}"
fi

# Vector 配置
if [ -f "$RESOURCES_DIR/vector/vector.toml" ]; then
    cp "$RESOURCES_DIR/vector/vector.toml" "$DEPLOY_DIR/vector/vector.toml"
    echo -e "${GREEN}✓ vector/vector.toml 已复制${NC}"
else
    echo -e "${YELLOW}⚠ vector/vector.toml 未找到，跳过${NC}"
fi

# Grafana 配置
if [ -d "$RESOURCES_DIR/grafana/provisioning" ]; then
    cp -r "$RESOURCES_DIR/grafana/provisioning" "$DEPLOY_DIR/grafana/"
    echo -e "${GREEN}✓ grafana/provisioning 已复制${NC}"
fi

# Prometheus 配置（优先使用项目中的，否则创建默认）
if [ -f "$RESOURCES_DIR/prometheus/prometheus.yml" ]; then
    cp "$RESOURCES_DIR/prometheus/prometheus.yml" "$DEPLOY_DIR/prometheus/prometheus.yml"
    echo -e "${GREEN}✓ prometheus/prometheus.yml 已复制${NC}"
else
    # 创建默认 Prometheus 配置
    cat > "$DEPLOY_DIR/prometheus/prometheus.yml" << 'PROMEOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'clickhouse'
    static_configs:
      - targets: ['clickhouse:8123']
    metrics_path: '/metrics'

  - job_name: 'grafana'
    static_configs:
      - targets: ['grafana:3000']
PROMEOF
    echo -e "${GREEN}✓ prometheus/prometheus.yml 已创建（默认配置）${NC}"
fi

echo ""

# 拉取镜像
echo -e "${YELLOW}[4/5] 拉取 Docker 镜像...${NC}"
cd "$DEPLOY_DIR"
sudo $DOCKER_COMPOSE pull 2>/dev/null || echo -e "${YELLOW}⚠ 部分镜像拉取失败，将在启动时自动拉取${NC}"
echo ""

# 启动服务
echo -e "${YELLOW}[5/5] 启动服务...${NC}"
sudo $DOCKER_COMPOSE up -d
echo ""

# 等待服务启动
echo -e "${YELLOW}等待服务启动（30秒）...${NC}"
sleep 30

# 验证部署
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查服务状态
echo -e "${YELLOW}服务状态：${NC}"
sudo $DOCKER_COMPOSE ps
echo ""

# 获取主机 IP
HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "localhost")

# 访问地址
echo -e "${YELLOW}访问地址：${NC}"
echo -e "  ClickVisual:  ${GREEN}http://${HOST_IP}:19001${NC}"
echo -e "  Grafana:      ${GREEN}http://${HOST_IP}:3000${NC} (admin / \$GRAFANA_PASSWORD)"
echo -e "  Prometheus:   ${GREEN}http://${HOST_IP}:9090${NC}"
echo ""

# 健康检查
echo -e "${YELLOW}服务健康检查：${NC}"
check_service() {
    local url=$1
    local name=$2
    if curl -s --max-time 3 "$url" > /dev/null 2>&1; then
        echo -e "  ${name}:   ${GREEN}✓ 正常${NC}"
    else
        echo -e "  ${name}:   ${RED}✗ 异常${NC}"
    fi
}

check_service "http://localhost:8123/ping" "ClickHouse"
check_service "http://localhost:19001" "ClickVisual"
check_service "http://localhost:3000" "Grafana"
check_service "http://localhost:9090" "Prometheus"

echo ""
echo -e "${YELLOW}常用命令：${NC}"
echo "  查看日志:     sudo $DOCKER_COMPOSE logs -f [服务名]"
echo "  重启服务:     sudo $DOCKER_COMPOSE restart [服务名]"
echo "  停止服务:     sudo $DOCKER_COMPOSE stop"
echo "  启动服务:     sudo $DOCKER_COMPOSE start"
echo "  查看状态:     sudo $DOCKER_COMPOSE ps"
echo ""
echo -e "${GREEN}部署完成！${NC}"
