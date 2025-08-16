FROM sbtscala/scala-sbt:eclipse-temurin-17.0.15_6_1.11.4_2.13.16 AS build
WORKDIR /forex
COPY . .
RUN sbt clean assembly

FROM eclipse-temurin:17.0.15_6-jre-noble
COPY --from=build /forex/target/scala-2.13/forex-assembly-1.0.1.jar /app.jar
ENTRYPOINT ["java","-cp","/app.jar","forex.Main"]