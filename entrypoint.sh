#!/bin/sh
set -e

# 启动后端（后台运行）
java -jar /app/app.jar &

# 等待后端启动（可选增强功能，防止nginx过早启动502）
sleep 2

# 启动 Nginx（前台运行，托管容器生命周期）
exec nginx -g 'daemon off;'
