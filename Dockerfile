FROM openjdk:8-jdk as BUILD

RUN mkdir /workspace
WORKDIR /workspace
COPY . .

RUN ./gradlew build -x test

FROM openjdk:8-jre as APP

COPY --from=BUILD /workspace/build/libs/*all.jar testcontainers-zap.jar

ENTRYPOINT ["java", "-jar", "./testcontainers-zap.jar"]
