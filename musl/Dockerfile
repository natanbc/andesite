FROM alpine:edge

ARG version

WORKDIR /andesite

COPY jlink.sh jlink.sh
COPY andesite-${version}-linux-musl-x86-64.jar andesite.jar

ENV ADDITIONAL_MODULES=jdk.crypto.ec,jdk.crypto.cryptoki

RUN apk add --no-cache openjdk15-jdk openjdk15-jmods jattach --repository http://dl-cdn.alpinelinux.org/alpine/edge/testing/ --virtual .java && \
    export PATH=/usr/lib/jvm/java-15-openjdk/bin/:$PATH && \
    ash jlink.sh andesite.jar && \
    apk del .java && \
    apk add --no-cache libstdc++ && \
    adduser -h /andesite -s /bin/false -D -H andesite && \
    mkdir logs && \
    chown -R andesite:andesite logs

RUN jrt/bin/java -Xshare:dump && \
    jrt/bin/java -XX:ArchiveClassesAtExit=app.jsa -jar andesite.jar cds && \
    rm -rf /tmp/* logs/*

USER andesite

CMD ["jrt/bin/java", "-XX:SharedArchiveFile=app.jsa", "-Djdk.tls.client.protocols=TLSv1,TLSv1.1,TLSv1.2", "-jar", "andesite.jar"]