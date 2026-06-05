#!/bin/bash
# SQL 方言防回归守卫：迁移到 PostgreSQL 17 后，禁止 MySQL 方言重新进入主代码。
# 在 CI / pre-commit 运行。命中任一 MySQL 专属模式即 fail。
if grep -rnE "ON DUPLICATE KEY|JSON_EXTRACT|INFORMATION_SCHEMA|MODIFY COLUMN" eify-*/src/main/java/; then
  echo "🚨 发现 MySQL 方言残留（破窗效应）！请使用 PostgreSQL 原生语法。"
  exit 1
else
  echo "✅ SQL 方言检查通过。"
  exit 0
fi
