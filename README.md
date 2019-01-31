# Andesite

Standalone, [mostly](#lavalink-compatibility) lavalink compatible audio sending node.

Support for andesite can be found in the [catnip guild](https://discord.gg/PVzMmea), 
on the #andesite channel.

# Features

- [lavadsp](https://github.com/natanbc/lavadsp) filter support
- ARM and Darwin (Mac) support
- [Plugin](PLUGINS.md) support
- [Debug](DEBUGGING.md) tooling
- Available over REST, WebSocket or [singyeong](https://github.com/queer/singyeong)
- [Prometheus](https://prometheus.io) metrics
- [Sentry](https://sentry.io) error reporting
- Mostly API compatible with [Lavalink](https://github.com/Frederikam/Lavalink)
- Support for lavaplayer non allocating frame buffer
- Detailed statistics about the JVM it's running on

# Installing

Grab a jar from the [releases page](https://github.com/natanbc/andesite-node/releases)
or use the [docker image](https://hub.docker.com/r/natanbc/andesite)

Andesite requires a JVM with Java 11 support.

# Configuration

See [CONFIGURATION.md](CONFIGURATION.md)

# Clients

Currently there are no Andesite specific clients, but most lavalink 3.x
clients should work, although missing support for andesite specific features.

- [Create your own](API.md)

# Lavalink Compatibility

The API is mostly compatible with lavalink. All lavalink opcodes except `configureResuming`
are compatible with Andesite. This incompatibility exists because Andesite implements resuming
in a different way, with keys generated server side.

