# 多阶段构建：Maven 打包 + JRE 运行（生产镜像以 JDK 11 为准）
FROM maven:3.8-openjdk-11 AS build
WORKDIR /build
COPY . .
RUN mvn -q package -DskipTests

FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=build /build/projlife-api/target/projlife-app.jar /app/projlife-app.jar
ENV DB_HOST=localhost DB_USERNAME=root DB_PASSWORD=change-me
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/projlife-app.jar"]
