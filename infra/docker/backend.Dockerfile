# Build context: backend/
# --- Giai đoạn 1: build file jar bằng Maven ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Tải dependency trước để Docker cache lớp này; chỉ sửa code thì không tải lại
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Test chạy ở bước riêng (mvn verify cần Testcontainers/Docker), không chạy trong lúc build image
RUN mvn -B -q -DskipTests package

# --- Giai đoạn 2: image chạy, chỉ có JRE + jar ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
