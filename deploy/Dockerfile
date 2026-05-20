# 后端多阶段构建
# 构建阶段：编译 Maven 项目
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先复制 pom 文件，利用 Docker 缓存层下载依赖
COPY pom.xml .
COPY eify-common/pom.xml eify-common/
COPY eify-app/pom.xml eify-app/
COPY eify-agent/pom.xml eify-agent/
COPY eify-chat/pom.xml eify-chat/
COPY eify-knowledge/pom.xml eify-knowledge/
COPY eify-mcp/pom.xml eify-mcp/
COPY eify-auth/pom.xml eify-auth/
COPY eify-provider/pom.xml eify-provider/
COPY eify-workflow/pom.xml eify-workflow/

# 下载依赖（独立缓存层，代码变更不会重新下载）
RUN mvn dependency:go-offline -q

# 复制源码并构建
COPY . .
RUN mvn clean package -DskipTests -q

# 运行阶段：仅包含 JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 时区设置
RUN apk add --no-cache tzdata wget && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY --from=builder /build/eify-app/target/eify-app-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -q --spider http://localhost:8080/api/v1/health || exit 1

# JAVA_OPTS 由 K8s ConfigMap 注入，默认值仅用于非 K8s 环境
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -jar app.jar"]
