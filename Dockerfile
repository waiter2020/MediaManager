# syntax=docker.m.daocloud.io/docker/dockerfile:1.7
#
# 国内默认：DaoCloud 缓存的基础镜像 + 阿里云 apk/apt + npmmirror + 阿里云 Maven。
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
ARG ALPINE_MIRROR_HOST=mirrors.aliyun.com
ARG DEBIAN_MIRROR_HOST=mirrors.aliyun.com
ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG MAVEN_SETTINGS_FILE=settings.xml
ARG MAVEN_THREADS=1C

# ---------- 国内镜像：Alpine apk ----------
FROM ${NODE_IMAGE} AS frontend-builder
ARG MIRROR_PROFILE
ARG ALPINE_MIRROR_HOST
ARG NPM_REGISTRY
WORKDIR /app
RUN if [ "$MIRROR_PROFILE" = "cn" ]; then \
      sed -i "s/dl-cdn.alpinelinux.org/${ALPINE_MIRROR_HOST}/g" /etc/apk/repositories; \
    fi
COPY media-manager-web/package.json media-manager-web/package-lock.json media-manager-web/.npmrc ./
RUN if [ "$MIRROR_PROFILE" = "cn" ]; then \
      npm config set registry "${NPM_REGISTRY}"; \
    fi
ENV NPM_CONFIG_FETCH_RETRIES=5 \
    NPM_CONFIG_FETCH_RETRY_MINTIMEOUT=20000 \
    NPM_CONFIG_FETCH_RETRY_MAXTIMEOUT=120000 \
    NPM_CONFIG_PREFER_OFFLINE=true
RUN --mount=type=cache,id=npm-cache,target=/root/.npm \
    npm ci --ignore-scripts
COPY media-manager-web/ .
RUN --mount=type=cache,id=npm-cache,target=/root/.npm \
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
COPY --from=frontend-builder /app/dist ./src/main/resources/static
RUN --mount=type=cache,id=maven-repo,target=/root/.m2 \
    mvn -s /usr/share/maven/ref/settings.xml -T${MAVEN_THREADS} clean package -DskipTests -B -q

FROM ${JRE_IMAGE}
ARG MIRROR_PROFILE
ARG ALPINE_MIRROR_HOST
WORKDIR /app
RUN if [ "$MIRROR_PROFILE" = "cn" ]; then \
      sed -i "s/dl-cdn.alpinelinux.org/${ALPINE_MIRROR_HOST}/g" /etc/apk/repositories; \
    fi
RUN apk add --no-cache ffmpeg nginx curl dos2unix
COPY --from=backend-builder /app/target/media-manager.jar /app/app.jar
COPY --from=frontend-builder /app/dist /usr/share/nginx/html
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY entrypoint.sh /entrypoint.sh
RUN dos2unix /entrypoint.sh && chmod +x /entrypoint.sh && mkdir -p /run/nginx
EXPOSE 80
ENTRYPOINT ["/entrypoint.sh"]
