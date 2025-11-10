FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

RUN curl --fail -L "https://repo1.maven.org/maven2/io/github/tanin47/backdoor/2.2.0-rc1/backdoor-2.2.0-rc1.jar" -o backdoor.jar

#For testing with a local JAR
#COPY ./build/libs/backdoor-2.2.0-rc1.jar backdoor.jar
