# Connection Methods

Connections can be made over three methods:

- HTTP
- WebSocket

## Metadata

Andesite metadata is exposed by all default connection methods.

The current values are

| name | type | description |
|------|------|-------------|
| version | string | version of the node |
| version-major | string | major version of the node |
| version-minor | string | minor version of the node |
| version-revision | string | revision version of the node |
| version-commit | string | commit hash of the node |
| version-build | integer | build number provided by the CI |
| node-region | string | [region](https://github.com/natanbc/andesite-node/blob/master/CONFIGURATION.md#settings) defined in the config |
| node-id | string | [id](https://github.com/natanbc/andesite-node/blob/master/CONFIGURATION.md#settings) defined in the config |
| enabled-sources | list of strings | list of sources [enabled](https://github.com/natanbc/andesite-node/blob/master/CONFIGURATION.md#settings) in the config |
| loaded-plugins | list of strings | list of [plugins](https://github.com/natanbc/andesite-node/blob/master/PLUGINS.md) loaded |

Connection methods may choose to format these in a more idiomatic format.

## HTTP

All requests must have an `Authorization` header or `password` query param
with the password* defined in the config. Furthermore, all routes related
to players, including the [WebSocket](#websocket) routes, must have an
`User-Id` header or `user-id` query param with the bot's id. For both of
them, the header takes priority.

If an error happens, an [error](#error) object is returned. Since these objects can get quite big, it's possible
to enable a shorter version, which does not send stack frames, by providing a header named `Andesite-Short-Errors`
or a query param named `shortErrors`. Their values can be anything, as long as they are present shorter error messages
are returned.

Node metadata is exposed in response headers as dash (`-`) separated `Title Case`
identifiers, prepended with `Andesite-`, eg `version-major` becomes `Andesite-Version-Major`.
Lists have their values joined by a string, eg `[a, b]` becomes `a,b`.

\* If the password isn't defined or is null this header may be omitted.

### Player routes

| route | description | returns player |
|-------|-------------|----------------|
| POST /player/voice-server-update | provides a voice server update event | |
| GET /player/:guild_id | returns the state of the player for that guild | x |
| POST /player/:guild_id/play | plays a track on the guild. Body must be a valid [play](#play) payload | x |
| POST /player/:guild_id/stop | stops playing audio on the guild. | x |
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
| GET /stats/lavalink | returns lavalink compatible stats about the node |
| GET /loadtracks | loads tracks from the `identifier` query param. Returns a [loaded tracks](#loaded-tracks) response |
| GET /decodetrack | returns metadata for a track in the `track` query param. Returns a [track info](#track-info) object |
| POST /decodetrack | returns metadata for a track in the `track` body property. Returns a [track info](#track-info) object |
| POST /decodetracks | returns metadata for the tracks in the request body. Returns an array of [track info](#track-info) objects |

## WebSocket

- The regular websocket is available on the `/websocket` route.
- A (mostly*) lavalink compatible websocket is available by default on the `/` route. 

Websocket authorization is the same as HTTP, as has the same metadata sent in the response.

Additionally, the `metadata` payload sent on connection start provides the metadata
for clients which don't have access to the handshake response headers. The values are
sent with unmodified keys, and the appropriate json types. Lists are converted into arrays,
other values are sent as-is.

All payloads must be valid json objects. If parsing fails, the socket is closed with code 4001.
Both text and binary frames are accepted, as long as the data is valid UTF 8.

All payloads must have an `op` key, which must be a string. This restriction does not apply to
custom handling of payloads done by plugins.

Responses from the server will always contain an `op` 

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
| get-player | returns the player state |
| get-stats | returns node stats |
| ping | used to calculate the ping and/or get the user id used in the handshake header. sends the received payload back |

Additionally, WebSockets offer a replay system, so events fired after a connection was closed can be replayed.
During websocket connection, the server will send a header `Andesite-Connection-Id`. After the websocket connection
is established, clients can send an [event-buffer](#event-buffer) payload to enable buffering. When a connection is closed,
events will be buffered for up to the timeout specified in the `event-buffer` payload. After that, all buffered events
are discarded and buffering will stop. To get the missed events, clients must reconnect with a `Andesite-Resume-Id`
header containing the value returned by the `Andesite-Connection-Id` received earlier. All IDs are single use and
reconnects must save the new ID returned. To disable buffering, simply send an `event-buffer` payload with timeout of 0.

All commands that directly interact with players (updating it, changing it's state, etc) send a player update
as a response.

Events that may be sent by the websocket are:

* Connection ID (`connection-id` op, not sent on lavalink compat)

| key | type | description |
|-----|------|-------------|
| id | string | ID of the connection |

* Metadata (`metadata` op, not sent on lavalink compat)

| key | type | description |
|-----|------|-------------|
| data | object | map of metadata key to value. Values may be integers, strings or arrays of strings |

* Player Update (`player-update` op, `playerUpdate` on lavalink compat)

| key | type | description |
|-----|------|-------------|
| userId | string | ID of the user that owns the player |
| guildId | string | ID of the guild that owns the player |
| state | [player](#player) | State of the player |

* Event (`event` op)

| key | type | description |
|-----|------|-------------|
| type | string | One of TrackStartEvent, TrackEndEvent, TrackExceptionEvent, TrackStuckEvent, WebSocketClosedEvent |
| userId | string | ID of the user affected by this event |
| guildId | string | ID of the guild affected by this event |

Additional event-specific data is also included:

  * reason (string), code (integer), byRemote (boolean) for WebSocketClosedEvent
  * track (string) for TrackStartEvent
  * track (string), reason (string), mayStartNext (boolean) for TrackEndEvent
  * track (string), error (string), exception(short [error](#error)) for TrackExceptionEvent
  * track (string), thresholdMs (integer) for TrackStuckEvent

* Pong (`pong` op)

| key | type | description |
|-----|------|-------------|
| userId | string | default user ID for this connection, provided on the ws handshake |

* Stats (`stats` op)

| key | type | description |
|-----|------|-------------|
| userId | string | default user ID for this connection, provided on the ws handshake |
| stats | object | map containing the node stats |

\* Lavalink resumes are not supported.

### Browser 

The WebSocket endpoints can be accessed from a browser with a few small changes to how the connection
is established.

The password must be provided as a websocket extension or query param.

The user id must be provided as a query param.

```js
var ws = new WebSocket("ws://my.node/websocket?user-id=1234&password=youshallnotpass")
```
or
```js
var ws = new WebSocket("ws://my.node/websocket?user-id=1234", "andesite-password:youshallnotpass")
```

After the connection is established, the API is identical to regular websockets.

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
| time | string | current unix timestamp on the node |
| position | integer/null | position of the current playing track, or null if nothing is playing |
| paused | boolean | whether or not the player is paused |
| volume | integer | the volume of the player |
| filters | object | map of filter name -> filter settings for each filter present |
| mixer | object | map of mixer player id -> [mixer player](#mixer-player) |
| mixerEnabled | boolean | whether or not the mixer is the current source of audio |

## Mixer Player

| key | type | description |
|-----|------|-------------|
| time | string | current unix timestamp on the node |
| position | integer/null | position of the current playing track, or null if nothing is playing |
| paused | boolean | whether or not the player is paused |
| volume | integer | the volume of the player |
| filters | object | map of filter name -> filter settings for each filter present |

## Voice Server Update

| key | type | description |
|-----|------|-------------|
| sessionId | string | session ID for the current user in the event's guild |
| guildId | string | ID of the guild |
| event | voice-server-update | voice server update event sent by discord |

## Play

| key | type | description |
|-----|------|-------------|
| track | string | base64 encoded lavaplayer track. If null, the player is stopped. Only use null for mixer players, for regular players use stop instead. |
| start | integer/null | timestamp, in milliseconds, to start the track |
| end | integer/null | timestamp, in milliseconds, to end the track |
| pause | boolean/null | whether or not to pause the player |
| volume | integer/null | volume to set on the player |
| noReplace | boolean | if true and a track is already playing/paused, this command is ignored |

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
| volume | [volume](#volume-update)/null | configures the volume filter |

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
| enable | boolean/null | if present, controls whether or not the mixer should be used |
| players | object | map of player id to [play](#play)/[update](#update) payloads for each mixer source |

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

## Volume {#update}

| key | type | default |
|-----|------|---------|
| volume | float | 1 |
