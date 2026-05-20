#!/usr/bin/env bash
# ============================================================
# Eify 版本号递增脚本
# 用法: ./scripts/release.sh
#
# 流程（在云端自动打 Tag 之后执行）:
#   main 上当前: 1.0.0-SNAPSHOT / 1.0.0-Dev
#   执行后:      1.0.1-SNAPSHOT / 1.0.1-Dev
# ============================================================
set -euo pipefail

# 检查 git 状态
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "错误: 当前目录不是 git 仓库"
    exit 1
fi

BRANCH=$(git branch --show-current)
if [ "$BRANCH" != "main" ]; then
    echo "错误: 请在 main 分支上执行 (当前: $BRANCH)"
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "错误: 工作区有未提交的更改，请先提交或 stash"
    exit 1
fi

# 提取当前 dev 版本号 (从 pom.xml 的 eify.version 属性)
CURRENT_SNAPSHOT=$(grep '<eify.version>' pom.xml | sed 's/.*<eify.version>\(.*\)<\/eify.version>/\1/')
CURRENT_VERSION="${CURRENT_SNAPSHOT%-SNAPSHOT}"

if [ -z "$CURRENT_VERSION" ]; then
    echo "错误: 无法从 pom.xml 读取当前版本"
    exit 1
fi

# 计算下一个 patch 版本号
IFS='.' read -r major minor patch <<< "$CURRENT_VERSION"
NEXT_VERSION="${major}.${minor}.$((patch + 1))"

echo ""
echo "=========================================="
echo "  版本递增"
echo "  $CURRENT_VERSION-SNAPSHOT  →  $NEXT_VERSION-SNAPSHOT"
echo "  $CURRENT_VERSION-Dev      →  $NEXT_VERSION-Dev"
echo "=========================================="
echo ""

# 后端: pom.xml
sed -i "s|<version>${CURRENT_VERSION}-SNAPSHOT</version>|<version>${NEXT_VERSION}-SNAPSHOT</version>|" pom.xml
sed -i "s|<eify.version>${CURRENT_VERSION}-SNAPSHOT</eify.version>|<eify.version>${NEXT_VERSION}-SNAPSHOT</eify.version>|" pom.xml

# 前端: package.json + package-lock.json
sed -i "s|\"version\": \"${CURRENT_VERSION}-Dev\"|\"version\": \"${NEXT_VERSION}-Dev\"|" eify-web/package.json
sed -i "s|\"version\": \"${CURRENT_VERSION}-Dev\"|\"version\": \"${NEXT_VERSION}-Dev\"|g" eify-web/package-lock.json

git add pom.xml eify-web/package.json eify-web/package-lock.json
git commit -m "prepare for next development $NEXT_VERSION"

echo ""
echo "完成。发布到云端后，云端会自动打 Tag v$CURRENT_VERSION"
echo "手动推送: git push origin main"
