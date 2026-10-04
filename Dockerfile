FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /source
COPY pom.xml ./
COPY sky-common ./sky-common
COPY sky-pojo ./sky-pojo
COPY sky-server ./sky-server
COPY knowledge ./knowledge
# 容器构建只编译打包；真实数据库测试在单独验收步骤执行，镜像构建不接触宿主数据库。
RUN mvn -B -ntp -DskipTests package
COPY docker/HealthProbe.java /tmp/HealthProbe.java
RUN javac -encoding UTF-8 -d /tmp /tmp/HealthProbe.java

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system sky && useradd --system --gid sky sky
COPY --from=build --chown=sky:sky /source/sky-server/target/sky-server-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=build /tmp/HealthProbe.class /app/HealthProbe.class
USER sky
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
