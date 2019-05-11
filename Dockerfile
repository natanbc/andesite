FROM openjdk:12 AS builder

ARG version

WORKDIR /andesite

COPY jlink.sh jlink.sh
COPY andesite-node-${version}-all.jar andesite.jar
COPY jattach-debug-plugin-${version}.jar plugins/jattach-debug.jar
COPY jfr-debug-plugin-${version}.jar plugins/jfr-debug.jar

ENV ADDITIONAL_MODULES=jdk.crypto.ec

RUN ["bash", "jlink.sh", "andesite.jar", "plugins/jattach-debug.jar", "plugins/jfr-debug.jar"]

FROM frolvlad/alpine-glibc:alpine-3.9

ARG jattachVersion

WORKDIR /andesite

RUN apk add --no-cache libstdc++

RUN wget "https://www.archlinux.org/packages/core/x86_64/zlib/download" -O /tmp/libz.tar.xz \
    && mkdir -p /tmp/libz \
    && tar -xf /tmp/libz.tar.xz -C /tmp/libz \
    && cp /tmp/libz/usr/lib/libz.so.1.2.11 /usr/glibc-compat/lib \
    && /usr/glibc-compat/sbin/ldconfig \
    && rm -rf /tmp/libz /tmp/libz.tar.xz

RUN wget https://github.com/apangin/jattach/releases/download/$jattachVersion/jattach -O /bin/jattach
RUN chmod +x /bin/jattach

COPY --from=builder /andesite /andesite

CMD ["jrt/bin/java", "-jar", "andesite.jar"]
