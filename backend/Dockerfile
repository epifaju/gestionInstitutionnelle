FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
    && addgroup -g 1001 app \
    && adduser -D -u 1001 -G app app \
    && mkdir -p /var/log/gestion \
    && chown -R app:app /var/log/gestion
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 1001
EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
