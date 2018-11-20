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
| Andesite-Node-Region | [node region](CONFIGURATION.md#settings) defined in the config |
| Andesite-Node-Id | [node ID](CONFIGURATION.md#settings) defined in the config |

\* If the key is missing or null this header may be omitted.

### Player routes

| route | description | returns player |
|-------|-------------|----------------|
| POST /player/voice-server-update | provides a voice server update event | |
| GET /player/:guild_id | returns the state of the player for that guild | x |
| POST /player/:guild_id/play | plays a track on the guild. Body must be a valid [play](#play) payload | x |
| POST /player/:guild_id | stops playing audio on the guild. | x |
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

- A lavalink compatible websocket is available by default on the `/lavalink` route.

All payloads must be valid json objects. If parsing fails, the socket is closed with code 4001.

All payloads must have `op` and `guildId` keys, which must be strings.

Valid `op`s are:

| op | description |
|----|-------------|
| voice-server-update | provides a voice server update. Payload must also be a valid [voice server update](#voice-server-update) object |
| play | plays a track on the guild. Payload must also be a valid [play](#play) object |
| stop | stops playing on the guild |
| pause | updates the pause state on the guild. Payload must also be a valid [pause](#pause) object |
| seek | updates the track position. Payload must also be a valid [seek](#seek) object |
| volume | updates the volume on the guild. Payload must also be a valid [volume](#volume) object |
| update | updates the player. Payload must also be a valid [update](#update) object |
| destroy | destroys the player. Resulting player update event will have a `destroyed` key with value of `true` |
| get-stats | returns node stats |
| subscribe | subscribes this connection to receive events from the guild (lavalink connections are automatically subscribed on `play` requests) |



## Singyeong

Andesite adds the following metadata values:

- andesite-version: version of the node
- andesite-region: [region](CONFIGURATION.md#settings) defined in the config
- andesite-id: [id](CONFIGURATION.md#settings) defined in the config
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

## Update

| key | type | description |
|-----|------|-------------|
| pause | boolean/null | whether or not to pause the player |
| position | integer/null | timestamp to set the current track to, in milliseconds |
| volume | integer/null | volume to set on the player |

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