FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY ./build/staging-deploy/io/github/tanin47/backdoor/**/*.jar backdoor.jar
