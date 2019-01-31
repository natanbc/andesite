# Connection Methods

Connections can be made over three methods:

- HTTP
- WebSocket
- [Singyeong](https://github.com/queer/singyeong)

## HTTP

All requests must have an `Authorization` header with the password*
defined in the config. Furthermore, all routes related to players,
including the [WebSocket](#websocket) routes, must have an `User-Id` header
with the bot's id.

All responses have the following headers:

| name | value |
|------|-------|
| Andesite-Version | version of the node |
| Andesite-Version-Major | major version of the node |
| Andesite-Version-Minor | minor version of the node |
| Andesite-Version-Revision | revision version of the node |
| Andesite-Version-Commit | commit hash of the node |
| Andesite-Node-Region | [node region](CONFIGURATION.md#settings) defined in the config |
| Andesite-Node-Id | [node ID](CONFIGURATION.md#settings) defined in the config |
| Andesite-Enabled-Sources | comma separated list of [sources](CONFIGURATION.md#settings) enabled in the config |
| Andesite-Loaded-Plugins | comma separated list of [plugins](PLUGINS.md) loaded |

If an error happens, an [error](#error) object is returned. Since these objects can get quite big, it's possible
to enable a shorter version, which does not send stack frames, by providing a header named `Andesite-Short-Errors`
or a query param named `shortErrors`. Their values can be anything, as long as they are present shorter error messages
are returned. 

\* If the password isn't defined or is null this header may be omitted.

### Player routes

| route | description | returns player |
|-------|-------------|----------------|
| POST /player/voice-server-update | provides a voice server update event | |
| GET /player/:guild_id | returns the state of the player for that guild | x |
| POST /player/:guild_id/play | plays a track on the guild. Body must be a valid [play](#play) payload | x |
| POST /player/:guild_id | stops playing audio on the guild. | x |
| PATCH /player/:guild_id/mixer | configures the mixer for the guild. Body must be a valid [mixer update](#mixer-update) payload | x |
| PATCH /player/:guild_id/filters | configures the audio filters for the guild. Body must be a valid [filter update](#filter-update) payload | x |
| PATCH /player/:guild_id/pause | update pause state of the player. Body must be a valid [pause](#pause) payload | x |
| PATCH /player/:guild_id/seek | update the track position. Body must be a valid [seek](#seek) payload | x |
| PATCH /player/:guild_id/volume | update volume of the player. Body must be a valid [volume](#volume) payload | x |
| PATCH /player/:guild_id | update the player. Body must be a valid [update](#update) payload | x |
| DELETE /player/:guild_id | destroys the player | |

### Other routes

| route | description |
|-------|-------------|
| GET /stats | returns stats about the node |
| GET /stats/lavalink | returns lavalink compatible stats about the node. If the `detailed` query param is present, the detailed stats of the node are returned under the `detailed` key of the response |
| GET /loadtracks | loads tracks from the `identifier` query param. Returns a [loaded tracks](#loaded-tracks) response |
| GET /decodetrack | returns metadata for a track in the `track` query param. Returns a [track info](#track-info) object |
| POST /decodetrack | returns metadata for a track in the `track` body property. Returns a [track info](#track-info) object |
| POST /decodetracks | returns metadata for the tracks in the request body. Returns an array of [track info](#track-info) objects |

## WebSocket

- The regular websocket is available on the `/websocket` route.
- A (mostly*) lavalink compatible websocket is available by default on the `/lavalink` route. 

All payloads must be valid json objects. If parsing fails, the socket is closed with code 4001.

All payloads must have `op` and `guildId` keys, which must be strings.

Payloads can override the `User-Id` header by providing an `userId` field. This override is
valid only for that payload. All responses will have an `userId` field containing either the
id provided in the override or the id provided in the headers during the websocket handshake.

Valid `op`s are:

| op | description |
|----|-------------|
| voice-server-update | provides a voice server update. Payload must also be a valid [voice server update](#voice-server-update) object |
| play | plays a track on the guild. Payload must also be a valid [play](#play) object |
| mixer | configures the mixer for the guild. Payload must also be a valid [mixer update](#mixer-update) object |
| stop | stops playing on the guild |
| pause | updates the pause state on the guild. Payload must also be a valid [pause](#pause) object |
| seek | updates the track position. Payload must also be a valid [seek](#seek) object |
| volume | updates the volume on the guild. Payload must also be a valid [volume](#volume) object |
| filters | updates the player audio filters. Payload must also be a valid [filter update](#filter-update) object |
| update | updates the player. Payload must also be a valid [update](#update) object |
| destroy | destroys the player. Resulting player update event will have a `destroyed` key with value of `true` |
| get-player | returns the player state. |
| get-stats | returns node stats |
| subscribe | subscribes this connection to receive events from the guild (lavalink connections are automatically subscribed on `play` requests) |

Additionally, WebSockets offer a replay system, so events fired after a connection was closed can be replayed.
During websocket connection, the server will send a header `Andesite-Connection-Id`. After the websocket connection
is established, clients can send an [event-buffer](#event-buffer) payload to enable buffering. When a connection is closed,
events will be buffered for up to the timeout specified in the `event-buffer` payload. After that, all buffered events
are discarded and buffering will stop. To get the missed events, clients must reconnect with a `Andesite-Resume-Id`
header containing the value returned by the `Andesite-Connection-Id` received earlier. All IDs are single use and
reconnects must save the new ID returned. To disable buffering, simply send an `event-buffer` payload with timeout of 0.

Upon connecting, andesite will send an object containing the connection id. This can be used
to read it when the response headers are not exposed (eg vert.x websocket client).

```json
{
    "op": "connection-id",
    "id": "the id goes here"
}
```

\* Currently the stats sent always have a null `frameStats` key.

## Singyeong

Andesite adds the following metadata values:

- andesite-version: version of the node
- andesite-region: [region](CONFIGURATION.md#settings) defined in the config
- andesite-id: [id](CONFIGURATION.md#settings) defined in the config
- andesite-enabled-sources: list of sources [enabled](CONFIGURATION.md#settings) in the config
- andesite-players: `userid:guildid` list of the players being handled by this node

All payloads sent via singyeong are equal to those sent via [web socket](#websocket), except they also need
an `userId` key, which is sent on the handshake headers for websocket connections.

# Entities

## Loaded Track(s)

| key | type | description |
|-----|------|-------------|
| loadType | string | type* of the response |
| tracks | [track info](#track-info)[]/null | loaded tracks |
| playlistInfo | [playlist info](#playlist-info)/null | metadata of the loaded playlist |
| cause | [error](#error)/null | error that happened while loading tracks |
| severity | string/null | severity of the error |

\* Type may be one of

- TRACK_LOADED - `tracks` will only have one track, `playlistInfo`, `cause` and `severity` will be null.
- SEARCH_RESULT - `cause` and `severity` will be null.
- PLAYLIST_LOADED - `cause` and `severity` will be null.
- NO_MATCHES - all other fields will be null.
- LOAD_FAILED - `tracks` and `playlistInfo` will be null.

## Playlist Info

| key | type | description |
|-----|------|-------------|
| name | string | name of the playlist |
| selectedTrack | integer/null | index of the selected track in the `tracks` array, or null if no track is selected |

## Track Info

| key | type | description |
|-----|------|-------------|
| track | string | base64 encoded track |
| info | [track metadata](#track-metadata) | metadata of the track |

## Track Metadata

| key | type | description |
|-----|------|-------------|
| class | string | class name of the lavaplayer track |
| title | string | title of the track |
| author | string | author of the track |
| length | integer | duration of the track, in milliseconds |
| identifier | string | identifier of the track |
| uri | string | uri of the track |
| isStream | boolean | whether or not the track is a livestream |
| isSeekable | boolean | whether or not the track supports seeking |
| position | integer | current position of the track |


## Player 

| key | type | description |
|-----|------|-------------|
| time | integer | current unix timestamp on the node |
| position | integer/null | position of the current playing track, or null if nothing is playing |
| paused | boolean | whether or not the player is paused |
| volume | integer | the volume of the player |


## Voice Server Update

| key | type | description |
|-----|------|-------------|
| sessionId | string | session ID for the current user in the event's guild |
| guildId | string | ID of the guild |
| event | voice-server-update | voice server update event sent by discord |

## Play

| key | type | description |
|-----|------|-------------|
| track | string | base64 encoded lavaplayer track |
| start | integer/null | timestamp, in milliseconds, to start the track |
| end | integer/null | timestamp, in milliseconds, to end the track |
| pause | boolean/null | whether or not to pause the player |
| volume | integer/null | volume to set on the player |

## Pause

| key | type | description |
|-----|------|-------------|
| pause | boolean | whether or not to pause the player |

## Seek

| key | type | description |
|-----|------|-------------|
| position | integer | timestamp to set the current track to, in milliseconds |

## Volume

| key | type | description |
|-----|------|-------------|
| volume | integer | volume to set on the player |

## Filter update

| key | type | description |
|-----|------|-------------|
| equalizer | [equalizer](#equalizer)/null | configures the equalizer |
| karaoke | [karaoke](#karaoke)/null | configures the karaoke filter |
| timescale | [timescale](#timescale)/null | configures the timescale filter |
| tremolo | [tremolo](#tremolo)/null | configures the tremolo filter |
| vibrato | [vibrato](#vibrato)/null | configures the vibrato filter |
| volume | [volume](#volume-1)/null | configures the volume filter |

## Update

| key | type | description |
|-----|------|-------------|
| pause | boolean/null | whether or not to pause the player |
| position | integer/null | timestamp to set the current track to, in milliseconds |
| volume | integer/null | volume to set on the player |
| filters | [filter update](#filter-update)/null | configuration for the filters |

## Mixer Update

| key | type | description |
|-----|------|-------------|
| enable | boolean/null | if present, constrols whether or not the mixer should be used |
| players | object | map of source id to [play](#play)/[update](#update) payloads for each mixer source |

## Event Buffer

| key | type | description |
|-----|------|-------------|
| timeout | integer | timeout for event buffering, in milliseconds |

## Error

| key | type | description |
|-----|------|-------------|
| class | string | class of the error |
| message | string/null | message of the error |
| stack | [stack frame](#stack-frame)[] | stacktrace of the error |
| cause | [error](#error)/null | cause of the error |
| suppressed | [error](#error)[] | suppressed errors |

## Stack Frame

| key | type | description |
|-----|------|-------------|
| classLoader | string/null | name of the classloader |
| moduleName | string/null | name of the module |
| moduleVersion | string/null | version of the module |
| className | string | name of the class |
| methodName | string | name of the method |
| fileName | string/null | name of the source file |
| lineNumber | integer/null | line in the source file |
| pretty | string | pretty printed version of this frame, as it would appear on Throwable#printStackTrace |

# Filters

## Equalizer

| key | type | description |
|-----|------|-------------|
| bands | [band](#band)[] | array of bands to configure |

### Band

| key | type | description | default |
|-----|------|-------------|---------|
| band | integer (0-14) | band number to configure | - |
| gain | float ([-0.25, 1.0]) | value to set for the band | 0 |

## Karaoke

| key | type | default |
|-----|------|---------|
| level | float | 1 |
| monoLevel | float | 1 |
| filterBand | float | 220 |
| filterWidth | float | 100 |

## Timescale

| key | type | description | default |
|-----|------|-------------|---------|
| speed | float (> 0) | speed to play music at | 1 |
| pitch | float (> 0) | pitch to set | 1 |
| rate | float (> 0) | rate to set | 1 |

## Tremolo

| key | type | default |
|-----|------|---------|
| frequency (> 0) | float | 2 |
| depth (0 < x <= 1) | float | 0.5 |

## Vibrato

| key | type | default |
|-----|------|---------|
| frequency (0 < x <= 14) | float | 2 |
| depth (0 < x <= 1) | float | 0.5 |

## Volume

| key | type | default |
|-----|------|---------|
| volume | float | 1 |
