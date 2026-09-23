# ==========================================
# 前端多阶段构建 Dockerfile (Node.js 20 + Nginx Alpine)
# ==========================================

# 阶段 1: 构建阶段
FROM node:20-alpine AS build-stage
WORKDIR /app

COPY package*.json ./
RUN npm config set registry https://registry.npmmirror.com && \
    npm install

COPY . .
RUN npm run build

# 阶段 2: 运行阶段 (Nginx 静态代理)
FROM nginx:1.25-alpine AS production-stage
WORKDIR /usr/share/nginx/html

RUN rm -rf ./*
COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
