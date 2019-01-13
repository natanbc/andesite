FROM openjdk:11-jre-slim

ARG version

WORKDIR /andesite

COPY andesite-node-${version}-all.jar andesite.jar

CMD ["java", "-jar", "andesite.jar"]
