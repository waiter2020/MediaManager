# syntax=docker/dockerfile:1.7

FROM node:18-alpine AS frontend-builder
WORKDIR /app
# 替换 Alpine 源
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories
COPY media-manager-web/package.json media-manager-web/pnpm-lock.yaml* ./
RUN npm config set registry https://registry.npmmirror.com && \
    corepack enable && \
    pnpm config set registry https://registry.npmmirror.com
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --no-frozen-lockfile
COPY media-manager-web/ .
RUN pnpm build

FROM maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /app
COPY settings.xml /usr/share/maven/ref/
COPY media-manager-server/pom.xml .
# Download dependencies first to cache them
RUN --mount=type=cache,id=maven-repo,target=/root/.m2 \
    mvn -s /usr/share/maven/ref/settings.xml dependency:go-offline -B
COPY media-manager-server/src ./src
# Copy built frontend to spring boot static directory
COPY --from=frontend-builder /app/dist ./src/main/resources/static
RUN --mount=type=cache,id=maven-repo,target=/root/.m2 \
    mvn -s /usr/share/maven/ref/settings.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 替换 Alpine 源
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# 安装 FFmpeg 与 Nginx 与 Curl (用于健康检查)
RUN apk add --no-cache ffmpeg nginx curl dos2unix

# 拷贝后端 Jar
COPY --from=backend-builder /app/target/media-manager.jar /app/app.jar

# 拷贝前端构建产物到 Nginx 静态目录
COPY --from=frontend-builder /app/dist /usr/share/nginx/html

# 拷贝 Nginx 配置与启动脚本
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY entrypoint.sh /entrypoint.sh
RUN dos2unix /entrypoint.sh && chmod +x /entrypoint.sh && mkdir -p /run/nginx

EXPOSE 80

ENTRYPOINT ["/entrypoint.sh"]
