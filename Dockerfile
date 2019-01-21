FROM openjdk:11-jre-slim

ARG version
ARG jattachVersion

RUN apt-get update
RUN apt-get install -y wget
RUN wget https://github.com/apangin/jattach/releases/download/$jattachVersion/jattach -O /bin/jattach
RUN chmod +x /bin/jattach

WORKDIR /andesite

COPY andesite-node-${version}-all.jar andesite.jar
COPY jattach-debug-plugin-${version}.jar plugins/jattach-debug.jar

CMD ["java", "-jar", "andesite.jar"]