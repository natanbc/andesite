# Configuration

Andesite can be configured from 3 sources

- System environment variables
- JVM system properties
- A JSON file named `config.json`¹ in the application working directory

Keys are loaded by default in the order `file, system properties, environment variables`²


¹ This name can be changed with the `config-file` setting (file is ignored when reading this setting).

² This order can be changed with the `andesite.config.load-order` JVM system property, as a comma separated list of
the sources to use (valid sources are `file`, `props` and `env`).

## System properties

System properties are read from the `andesite.<name>` JVM system property.

Examples:

| setting | system property |
|---------|-----------------|
| x | andesite.x |
| x.y | andesite.x.y |
| x.y-z | andesite.x.y-z |

## Environment variables

Environment variables are read from the `ANDESITE_<name, uppercase, dots and slashes replaced with underscores>` variable.

Example:

| setting | system property |
|---------|-----------------|
| x | ANDESITE_X |
| x.y | ANDESITE_X_Y |
| x.y-z | ANDESITE_X_Y_Z |

# Settings

| key | type | description | default |
|-----|------|-------------|---------|
| extra-plugins | string | comma separated list of paths to load plugins from, besides the default path | null |
| password | string | password to use for http/websocket access. No filtering is done if null | null |
| debug-password | string | password to use for debug routes. If missing or null, the regular password is used instead. | null |
| log-level | string | lowest level to log | INFO |
| audio-handler | string | audio handler implementation to use. currently only `magma` is supported | magma |
| magma.array-provider | string | either `create-new` or `reuse-existing`. reuse-existing is more efficient, but only works with specific JVMs. Don't complain if it crashes. If it doesn't crash, it's most likely safe to use | create-new |
| lavalink.ws-path | string | route to run the lavalink websocket on. | /lavalink |
| send-system.type* | string | type of send system to use. Valid options are `nio`, `jda` and `nas` | `nas` on supported environments, `nio` otherwise |
| send-system.async | boolean | whether or not to use jda-async-packet-provider to wrap the send system | true |
| send-system.nas-buffer | integer | buffer duration, in milliseconds, to keep in native code. Ignored if type isn't `nas` | 400 |
| send-system.non-allocating | boolean | whether or not to use the non allocating frame buffer | false |
| jfr.enabled | boolean | whether or not to enable [JFR debug routes](https://github.com/natanbc/andesite-node/blob/master/DEBUGGING.md) | true |
| node.region | string | region of the node | "unknown" |
| node.id | string | id of the node | "unknown" |
| prometheus.enabled | boolean | whether or not to enable prometheus metrics | false |
| prometheus.path | string | path to collect prometheus metrics, uses the http port | /metrics |
| sentry.dsn | string | sentry dsn to report errors | null |
| sentry.tags | string | comma separated list of `key:value` pairs for sentry tags | null |
| sentry.log-level | string | lowest level to send to sentry | WARN |
| transport.http.port | integer | port to run the http/websocket server | 5000 |
| transport.http.rest | boolean | whether or not to enable the http api | true |
| transport.http.ws | boolean | whether or not to enable the websocket api | true |
| transport.singyeong.enabled | boolean | whether or not to enable the singyeong api | false |
| transport.singyeong.dsn | string | singyeong [dsn](#singyeong-dsn-format) for connecting | null |
| source.bandcamp | boolean | whether or not to enable playing and resolving tracks from bandcamp | true |
| source.beam | boolean | whether or not to enable playing and resolving tracks from beam | true |
| source.http | boolean | whether or not to enable playing and resolving tracks from http urls | **false** |
| source.local | boolean | whether or not to enable playing and resolving tracks from local files | **false** |
| source.soundcloud | boolean | whether or not to enable playing and resolving tracks from soundcloud | true |
| source.twitch | boolean | whether or not to enable playing and resolving tracks from twitch | true |
| source.vimeo | boolean | whether or not to enable playing and resolving tracks from vimeo | true |
| source.youtube | boolean | whether or not to enable playing and resolving tracks from youtube | true |

\* When running on architectures not supported by [jda-nas](https://github.com/sedmelluq/jda-nas), such as
ARM or Darwin devices, you must use either `jda` or `nio` for the send system. For production, nio is preferred
as it doesn't spawn a thread per voice connection. The default is changed to nio when running on unsupported
architectures.

### Singyeong DSN Format

A singyeong DNS is an URI with the format

```
<protocol>://<app id>[:<password>]@host[:port]
```

| key | description |
|-----|-------------|
| protocol | `singyeong` or `ssingyeong` (`ws` vs `wss`) |
| app id | app id to use |
| password | password to use, optional |
| host | host to connect |
| port | port to connect, defaults to 80 or 443, depending on the protocol |

- `singyeong://andesite@localhost` - valid, connects to port 80 on the current machine
- `ssingyeong://andesite@localhost` - valid, connects to port 443 on the current machine
- `wss://andesite@localhost` - invalid, protocol must be `singyeong` or `ssingyeong`
- `singyeong://andesite:youshallnotpass@localhost:1234` - valid, connects with password `youshallnotpass` to port
`1234` of the current machine
