# Andesite

Standalone, [mostly](#lavalink-compatibility) lavalink compatible audio sending node.

Support for andesite can be found in the [amyware server](https://discord.gg/PVzMmea), 
on the #andesite channel.

# Features

- [lavadsp](https://github.com/natanbc/lavadsp) filter support
- Supports multiple [architectures and OSes](https://github.com/natanbc/lp-cross)
- [Plugin](https://github.com/natanbc/andesite/blob/master/PLUGINS.md) support
- [Debug](https://github.com/natanbc/andesite/blob/master/DEBUGGING.md) tooling
- Available over REST or WebSocket
- [Prometheus](https://prometheus.io) metrics
- [Sentry](https://sentry.io) error reporting
- Mostly API compatible with [Lavalink](https://github.com/Frederikam/Lavalink)
- Support for lavaplayer non allocating frame buffer
- Detailed statistics about the JVM it's running on
- Can be used from a [browser](https://github.com/natanbc/andesite/blob/master/API.md#browser)

# Installing

Grab a jar from the [releases page](https://github.com/natanbc/andesite/releases)
or use the [docker image](https://hub.docker.com/r/natanbc/andesite)

Andesite requires a JVM with Java 15 support.

A musl-based docker image can be built with `DOCKERFILE=musl/Dockerfile ./gradlew docker`

# Configuration

See [CONFIGURATION.md](https://github.com/natanbc/andesite/blob/master/CONFIGURATION.md)

# Clients

- [AndeClient](https://github.com/arudiscord/andeclient) - Java 11+
- [andesite.py](https://github.com/gieseladev/andesite.py) - Python
- [Granitepy](https://github.com/twitch0001/granitepy) - Python
- [discord.js-andesite](https://github.com/lolwastedjs/discord.js-andesite) - JavaScript
- [Create your own](https://github.com/natanbc/andesite/blob/master/API.md)
- Most lavalink 3.x clients should be compatible

# Lavalink Compatibility

The API is mostly compatible with lavalink. All lavalink opcodes except `configureResuming`
are compatible with Andesite. This incompatibility exists because Andesite implements resuming
in a different way, with keys generated server side.

