# ==========================================
# 后端多阶段构建 Dockerfile (Eclipse Temurin 17)
# ==========================================

# 阶段 1: 编译构建
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B || true

COPY src ./src
RUN mvn clean package -DskipTests

# 阶段 2: 生产运行阶段
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 安装中文字体与基础工具 (PDF 归档报告生成与健康探活必需)
RUN apt-get update && \
    apt-get install -y --no-install-recommends fonts-noto-cjk fontconfig curl && \
    fc-cache -fv && \
    rm -rf /var/lib/apt/lists/*

# 从构建阶段拷贝 JAR 运行包
COPY --from=builder /build/target/college-internship-backend-1.0.0-SNAPSHOT.jar app.jar

ENV SPRING_PROFILES_ACTIVE=dev \
    SERVER_PORT=8080 \
    TZ=Asia/Shanghai

EXPOSE 8080

# 阶段8 健康检查探针 (API-116/Health)
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD curl -f http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
