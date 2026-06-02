# syntax=docker.m.daocloud.io/docker/dockerfile:1.7
#
# 国内默认：DaoCloud 基础镜像 + 清华 apk + 阿里云 Maven；npm 默认官方源（见 NPM_REGISTRY）。
# 海外构建示例：
#   docker build --build-arg MIRROR_PROFILE=default \
#     --build-arg NODE_IMAGE=node:18-alpine \
#     --build-arg MAVEN_IMAGE=maven:3.9-eclipse-temurin-21 \
#     --build-arg JRE_IMAGE=eclipse-temurin:21-jre-alpine \
#     --build-arg MAVEN_SETTINGS_FILE=settings-default.xml .

ARG DOCKER_HUB_MIRROR=docker.m.daocloud.io
ARG NODE_IMAGE=${DOCKER_HUB_MIRROR}/library/node:20-alpine
ARG MAVEN_IMAGE=${DOCKER_HUB_MIRROR}/library/maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=${DOCKER_HUB_MIRROR}/library/eclipse-temurin:21-jre-alpine
ARG MIRROR_PROFILE=cn
ARG ALPINE_MIRROR_HOST=mirrors.tuna.tsinghua.edu.cn
ARG DEBIAN_MIRROR_HOST=mirrors.aliyun.com
ARG NPM_REGISTRY=https://registry.npmjs.org
ARG MAVEN_SETTINGS_FILE=settings.xml
ARG MAVEN_THREADS=1C

# ---------- 前端构建（npm 缓存 + node_modules 缓存加速 npm ci）----------
FROM ${NODE_IMAGE} AS frontend-builder
ARG MIRROR_PROFILE
ARG NPM_REGISTRY
WORKDIR /app
COPY media-manager-web/package.json media-manager-web/package-lock.json media-manager-web/.npmrc ./
# NPM_CONFIG_REGISTRY 覆盖 .npmrc；双缓存；lock 未变时 npm ci 可复用 node_modules
ENV NPM_CONFIG_REGISTRY=${NPM_REGISTRY} \
    NPM_CONFIG_AUDIT=false \
    NPM_CONFIG_FUND=false \
    NPM_CONFIG_PROGRESS=false \
    NPM_CONFIG_LOGLEVEL=warn \
    NPM_CONFIG_MAXSOCKETS=32 \
    NPM_CONFIG_FETCH_RETRIES=2 \
    NPM_CONFIG_FETCH_RETRY_MINTIMEOUT=5000 \
    NPM_CONFIG_FETCH_RETRY_MAXTIMEOUT=30000 \
    NPM_CONFIG_PREFER_OFFLINE=true
RUN --mount=type=cache,id=mediamanager-node-modules,target=/app/node_modules \
    --mount=type=cache,id=mediamanager-npm-cache,target=/root/.npm \
    npm ci --ignore-scripts --prefer-offline --no-audit --no-fund
COPY media-manager-web/ .
RUN --mount=type=cache,id=mediamanager-node-modules,target=/app/node_modules \
    --mount=type=cache,id=mediamanager-npm-cache,target=/root/.npm \
    npm run build

# ---------- 国内镜像：Debian/Ubuntu apt（Maven 阶段若需 apt）----------
FROM ${MAVEN_IMAGE} AS backend-builder
ARG MAVEN_SETTINGS_FILE
ARG MIRROR_PROFILE
ARG DEBIAN_MIRROR_HOST
ARG MAVEN_THREADS
WORKDIR /app
RUN if [ "$MIRROR_PROFILE" = "cn" ]; then \
      if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i "s|deb.debian.org|${DEBIAN_MIRROR_HOST}|g; s|security.debian.org|${DEBIAN_MIRROR_HOST}|g" \
          /etc/apt/sources.list.d/debian.sources; \
      elif [ -f /etc/apt/sources.list ]; then \
        sed -i "s|archive.ubuntu.com|${DEBIAN_MIRROR_HOST}|g; s|security.ubuntu.com|${DEBIAN_MIRROR_HOST}|g" \
          /etc/apt/sources.list; \
        sed -i "s|deb.debian.org|${DEBIAN_MIRROR_HOST}|g" /etc/apt/sources.list 2>/dev/null || true; \
      fi; \
    fi
COPY ${MAVEN_SETTINGS_FILE} /usr/share/maven/ref/settings.xml
COPY media-manager-server/pom.xml .
ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
RUN --mount=type=cache,id=maven-repo,target=/root/.m2 \
    mvn -s /usr/share/maven/ref/settings.xml -T${MAVEN_THREADS} dependency:go-offline -B -q
COPY media-manager-server/src ./src
RUN --mount=type=cache,id=maven-repo,target=/root/.m2 \
    mvn -s /usr/share/maven/ref/settings.xml -T${MAVEN_THREADS} clean package -DskipTests -B -q

# ---------- Runtime stage ─────────────────────────────────────
FROM ${JRE_IMAGE}

LABEL maintainer="MediaManager Team" \
      description="MediaManager - Media library management with AI-powered features" \
      version="1.0.0"

ARG MIRROR_PROFILE
ARG ALPINE_MIRROR_HOST
WORKDIR /app
RUN if [ "$MIRROR_PROFILE" = "cn" ]; then \
      ver="$(cut -d. -f1,2 </etc/alpine-release)"; \
      printf '%s\n' \
        "https://${ALPINE_MIRROR_HOST}/alpine/v${ver}/main" \
        "https://${ALPINE_MIRROR_HOST}/alpine/v${ver}/community" \
        > /etc/apk/repositories; \
    fi && \
    apk add --no-cache ffmpeg nginx && \
    ln -sf /dev/stdout /var/log/nginx/access.log && \
    ln -sf /dev/stderr /var/log/nginx/error.log && \
    mkdir -p /run/nginx

ENV JAVA_OPTS="-Xmx1024m -Xms256m"

COPY --from=backend-builder /app/target/media-manager.jar /app/app.jar
COPY --from=frontend-builder /app/dist /usr/share/nginx/html
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/entrypoint.sh"]
