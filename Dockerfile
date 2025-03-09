#образ без мавена
FROM eclipse-temurin:21-jdk
LABEL authors="vdavdov"
RUN apt-get update && \
 apt-get install -y maven && \
 apt-get clean
WORKDIR /app
COPY pom.xml /app
COPY src /app/src
RUN mvn clean package -DskipTests
RUN rm -rf /app/src
ENTRYPOINT ["java", "-jar", "/app/target/multon-cancel-dupl-1.0.jar"]