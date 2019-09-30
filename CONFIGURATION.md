# Configuration

Andesite can be configured from 2 sources, from highest to lowest priority

- A HOCON file named `application.conf` in the application working directory
- System environment variables (doesn't support arrays)

All andesite keys must be prefixed with `andesite.`. When using a HOCON file, they can be put inside a
block named `andesite`. Plugins may choose to use a different prefix.

An example config can be found [here](https://github.com/natanbc/andesite-node/blob/master/application.conf.example)

# Settings

| key | type | description | default |
|-----|------|-------------|---------|
| extra-plugins | string[] | array of paths to load plugins from, besides the default path | [] |
| password | string | password to use for http/websocket access. No filtering is done if null | null |
| debug-password | string | password to use for debug routes. If missing or null, the regular password is used instead. | null |
| log-level | string | lowest level to log | INFO |
| auto-ytsearch | boolean | whether or not andesite should automatically prepend `ytsearch:` to identifiers that don't match known prefixes when loading tracks | true |
| audio-handler | string | audio handler implementation to use. by default, `magma` and `koe` are supported. Plugins may [add more implementations](https://github.com/natanbc/andesite-node/blob/master/PLUGINS.md#custom-audio-handlers), in which case the fully qualified class name must be used | magma |
| node.region | string | region of the node | "unknown" |
| node.id | string | id of the node | "unknown" |
| transport.http.bind-address | string | address to bind the http/websocket server. 0.0.0.0 means all network interfaces on the machine | 0.0.0.0 |
| transport.http.port | integer | port to run the http/websocket server | 5000 |
| transport.http.rest | boolean | whether or not to enable the http api | true |
| transport.http.ws | boolean | whether or not to enable the websocket api | true |
| transport.singyeong.enabled | boolean | whether or not to enable the singyeong api | false |
| transport.singyeong.dsn | string | singyeong [dsn](#singyeong-dsn-format) for connecting | null |
| prometheus.enabled | boolean | whether or not to enable prometheus metrics | false |
| prometheus.path | string | path to collect prometheus metrics, uses the http port | /metrics |
| sentry.enabled | boolean | whether or not to enable sentry | false |
| sentry.dsn | string | sentry dsn to report errors | null |
| sentry.tags | string | comma separated list of `key:value` pairs for sentry tags | null |
| sentry.log-level | string | lowest level to send to sentry | WARN |
| lavaplayer.frame-buffer-duration | integer | duration of the frame buffer, in milliseconds. changes in filters/volume take at least this time to start applying | 5000 |
| lavaplayer.non-allocating | boolean | whether or not to use the non allocating frame buffer | true |
| lavaplayer.youtube.max-playlist-page-count | maximum number of pages loaded from one playlist. There are 100 tracks per page. | 6 |
| lavaplayer.youtube.mix-loader-max-pool-size | maximum number of threads used by the mix loader pool | 10 |
| source.bandcamp | boolean | whether or not to enable playing and resolving tracks from bandcamp | true |
| source.beam | boolean | whether or not to enable playing and resolving tracks from beam | true |
| source.http | boolean | whether or not to enable playing and resolving tracks from http urls | **false** |
| source.local | boolean | whether or not to enable playing and resolving tracks from local files | **false** |
| source.soundcloud | boolean | whether or not to enable playing and resolving tracks from soundcloud | true |
| source.twitch | boolean | whether or not to enable playing and resolving tracks from twitch | true |
| source.vimeo | boolean | whether or not to enable playing and resolving tracks from vimeo | true |
| source.youtube | boolean | whether or not to enable playing and resolving tracks from youtube | true |
| lavalink.ws-path | string | route to run the lavalink websocket on. | / |
| magma.send-system.type* | string | type of send system to use. Valid options are `nio`, `jda` and `nas` | `nas` on supported environments, `nio` otherwise |
| magma.send-system.nas-buffer | integer | buffer duration, in milliseconds, to keep in native code. Ignored if type isn't `nas` | 400 |
| koe.event-loop-type | string | type of event loop to use. Valid options are `epoll` and `nio` | epoll if supported (aka on linux) |
| koe.byte-buffer-allocator | string | type of byte buffer allocator to use. Valid options are `pooled` and `unpooled` | pooled |
| koe.high-priority | boolean | whether or not packets should be marked as high priority | true |

\* When running on architectures not supported by [jda-nas](https://github.com/sedmelluq/jda-nas), such as
ARM or Darwin devices, you must use either `jda` or `nio` for the send system. For production, nio is preferred
as it doesn't spawn a thread per voice connection. The default is changed to nio when running on unsupported
architectures. Note that **lavaplayer has no arm natives**, so you may encounter errors on some tracks. Most
youtube tracks won't have issues, but YMMV.

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
