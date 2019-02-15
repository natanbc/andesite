# Debugging

Andesite has plugins to support [JFR](https://openjdk.java.net/jeps/328) and [jattach](https://github.com/apangin/jattach)
for debugging. Prebuilt jars for them can be found on the [releases](https://github.com/natanbc/andesite-node/releases) page.

Both plugins are included by default on docker images.

## JFR

### GET /jfr/state

Returns the state of the current JFR recording.

| key | type | description |
|-----|------|-------------|
| recording | boolean | whether or not a recording is in progress |
| maxSize | integer | maximum size for the recording on disk, if auto dumping is enabled. May be absent. |
| size | integer | current size of the recording. May be absent. |
| id | integer | current recording id. May be absent. |
| duration | integer | current recording duration, in milliseconds. May be absent. |
| name | string | current recording name. May be absent |
| settings | object | map of the current recording settings. May be absent. |
| destination | string | path to write the data. May be absent. | 

### GET /jfr/events

Returns a list of available events.

#### Event

| key | type | description |
|-----|------|-------------|
| name | string | name of the event |
| label | string | human readable name that describes the event |
| description | string | short sentence that describes the event |
| id | integer | unique id for the event in the JVM | 

### POST /jfr/start

Starts recording. Only one recording may be active at a time.

Query params:

| key | type | description |
|-----|------|-------------|
| events | string | comma separated list of events to enable, or `all` to enable all |
| destination | string | path to store events in the disk. Events are kept in memory and may be dropped if absent |
| maxSize | integer | maximum size for the file on disk before old events start getting removed |

### POST /jfr/stop

Stops recording, optionally dumping to a file.

Query params:

| key | type | description |
|-----|------|-------------|
| path | string | path to store the events. May be absent |

## Jattach Plugin

All responses follow the format 

```json
{
    "exitCode": 123,
    "stderr": "",
    "stdout": ""
}
```

### GET /debug/agent-properties

Returns agent properties

### POST /debug/datadump

Prints heap and thread summary to the **JVM's stdout**. Returns no response to the client.
Only included to cover all jattach commands.

### GET /debug/file

Returns a file from the local machine. Useful to download heap dumps.

Query params:

| key | type | description |
|-----|------|-------------|
| path | string | path of the wanted file |

### POST /debug/heapdump

Dumps the JVM heap to the specified path.

Query params:

| key | type | description |
|-----|------|-------------|
| path | string | path to write the heap dump to |

### GET /debug/inspectheap

Returns a list of all live objects, including the number of instances, bytes used and class name,
grouped by class.

### POST /debug/jcmd

Runs a jcmd command. Use `help` or `help --all` for a list of valid commands.

Query params:

| key | type | description |
|-----|------|-------------|
| command | string | command to execute |

### POST /debug/load

Loads an agent library.

Query params:

| key | type | description |
|-----|------|-------------|
| path | string | path to the library |
| absolute | boolean | whether or not the provided path is absolute, defaults to false |
| options | string | options to pass to the agent. May be absent |

### GET /debug/printflag

Returns the value of a JVM flag.

Query params:

| key | type | description |
|-----|------|-------------|
| flag | string | flag to read |

### GET /debug/properties

Returns the JVM system properties present.

### POST /debug/setflag

Updates a flag. Not all flags may be updated. A list of flags that can be updated can be
obtained with

```
curl http://your-andesite-instance/debug/jcmd?command=VM.flags%20-all | jj stdout | grep manageable
``` 

Query params:

| key | type | description |
|-----|------|-------------|
| flag | string | flag to update |
| value | string | value to set |

### GET /debug/stack

Returns a thread dump of the VM