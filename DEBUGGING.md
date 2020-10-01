# Debugging

Andesite has plugins to support [jattach](https://github.com/apangin/jattach)
for debugging. Prebuilt jars for them can be found on the [releases](https://github.com/natanbc/andesite/releases) page.

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