# ============================================================
#  ai-table 镜像构建
#
#  分三个阶段，好处是最终镜像里**不含 Node、Maven 和源码**，
#  只有一个编译好的 jar + JRE，体积小、也不会泄露源码。
# ============================================================

# ---------- 阶段 1：构建前端页面 ----------
FROM node:20-alpine AS frontend
WORKDIR /build/frontend
# 先只拷依赖清单，这样改代码时这一层能复用缓存
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------- 阶段 2：构建后端（把前端产物塞进 jar）----------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml backend/
RUN mvn -f backend/pom.xml -B dependency:go-offline
COPY backend/src backend/src
# 前端的打包结果放到后端静态目录，最终由后端一起发出去
COPY --from=frontend /build/frontend/dist backend/src/main/resources/static
RUN mvn -f backend/pom.xml -B clean package -DskipTests

# ---------- 阶段 3：运行 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /build/backend/target/ai-table-backend-*.jar app.jar

# 用户上传的头像存在这里（compose 里挂出来，重建容器不丢）
RUN mkdir -p /app/uploads
VOLUME ["/app/uploads"]

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
