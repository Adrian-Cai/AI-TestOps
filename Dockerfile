# ========================================
# Stage 1: 构建前端
# ========================================
FROM node:20-alpine AS frontend-builder

WORKDIR /app/frontend

# 安装 pnpm (若项目使用 npm 可去掉此行)
# RUN npm install -g pnpm

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ========================================
# Stage 2: 构建后端 JAR
# ========================================
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-builder

WORKDIR /app

COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY src ./src

# 将前端构建产物拷贝到后端静态资源目录
COPY --from=frontend-builder /app/src/main/resources/static ./src/main/resources/static

# 跳过测试以加快构建速度（测试可在 CI 独立阶段运行）
RUN mvn package -DskipTests -B -q

# ========================================
# Stage 3: 运行镜像
# ========================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 设置亚洲时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 从构建阶段复制 JAR
COPY --from=backend-builder /app/target/*.jar app.jar

# 健康检查（使用根路径，项目未引入 actuator）
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
