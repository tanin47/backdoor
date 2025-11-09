FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

RUN curl --fail -L "https://repo1.maven.org/maven2/io/github/tanin47/backdoor/2.1.0/backdoor-2.1.0.jar" -o backdoor.jar

#For testing with a local JAR
#COPY ./build/libs/backdoor-2.1.0.jar backdoor.jar
