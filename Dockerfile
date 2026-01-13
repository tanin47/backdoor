FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY ./web/build/staging-deploy/io/github/tanin47/**/**/*.jar .

RUN find . -name "*-javadoc.jar" -type f -delete
RUN find . -name "*-sources.jar" -type f -delete
RUN mv $(find . -name "backdoor-*.jar") backdoor.jar

ENTRYPOINT ["java", "-jar", "backdoor.jar"]
