package andesite.node;

import andesite.node.util.RequestUtils;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

public class JattachDebug implements Plugin {
    private static final Logger log = LoggerFactory.getLogger(JattachDebug.class);

    @Override
    public boolean requiresRouter() {
        return true;
    }

    @Override
    public void configureRouter(@Nonnull NodeState state, @Nonnull Router router) {
        Router r = Router.router(state.vertx());

        r.route().handler(c -> {
            var config = state.config();
            var password = config.get("debug-password");
            if(password == null) {
                password = config.get("password");
            }
            if(password != null && !password.equals(RequestUtils.findPassword(c))) {
                c.response()
                        .setStatusCode(401)
                        .setStatusMessage("Unauthorized")
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                                .put("code", 401)
                                .put("message", "Unauthorized")
                                .toBuffer()
                        );
                return;
            }
            c.next();
        });

        r.get("/agent-properties").handler(c -> exec(c, "agentProperties"));

        r.post("/datadump").handler(c -> exec(c, "datadump"));

        r.get("/file").handler(c -> {
            var path = c.queryParams().get("path");
            if(path == null) {
                badRequest(c, "Missing path");
                return;
            }
            var f = new File(path);
            if(!f.exists()) {
                badRequest(c, "File not found");
                return;
            }
            if(!f.isFile()) {
                badRequest(c, "Not a file");
                return;
            }
            if(!f.canRead()) {
                badRequest(c, "File not readable");
                return;
            }
            log.info("Sending file " + f.getPath());
            c.response().sendFile(f.getPath());
        });

        r.post("/heapdump").handler(c -> {
            var path = c.queryParams().get("path");
            if(path == null) {
                badRequest(c, "Missing path");
                return;
            }
            exec(c, "dumpheap", path);
        });

        r.get("/inspectheap").handler(c -> exec(c, "inspectheap"));

        r.post("/jcmd").handler(c -> {
            var command = c.queryParams().get("command");
            if(command == null) {
                badRequest(c, "Missing command");
                return;
            }
            exec(c, "jcmd", command);
        });

        r.post("/load").handler(c -> {
            var path = c.queryParams().get("path");
            if(path == null) {
                badRequest(c, "Missing path");
                return;
            }
            var absolute = c.queryParams().get("absolute");
            if(absolute == null) {
                absolute = "false";
            }
            var options = c.queryParams().get("options");
            if(options == null) {
                exec(c, "load", path, absolute);
            } else {
                exec(c, "load", path, absolute, options);
            }
        });

        r.get("/printflag").handler(c -> {
            var flag = c.queryParams().get("flag");
            if(flag == null) {
                badRequest(c, "Missing flag");
                return;
            }
            exec(c, "printflag", flag);
        });

        r.get("/properties").handler(c -> exec(c, "properties"));

        r.post("/setflag").handler(c -> {
            var flag = c.queryParams().get("flag");
            if(flag == null) {
                badRequest(c, "Missing flag");
                return;
            }
            var value = c.queryParams().get("value");
            if(value == null) {
                badRequest(c, "Missing value");
                return;
            }
            exec(c, "setflag", flag, value);
        });

        r.get("/stack").handler(c -> exec(c, "threaddump"));

        router.mountSubRouter("/debug", r);
    }

    private static void exec(RoutingContext context, String... command) {
        log.info("Executing command " + Arrays.toString(command));
        try {
            var list = new ArrayList<String>();
            list.add("jattach");
            list.add(String.valueOf(ProcessHandle.current().pid()));
            Collections.addAll(list, command);
            File tmpOut = File.createTempFile("stdout", ".txt");
            File tmpErr = File.createTempFile("stderr", ".txt");
            var process = new ProcessBuilder()
                    .command(list)
                    .redirectOutput(tmpOut)
                    .redirectError(tmpErr)
                    .start();
            process.onExit().thenRun(() -> sendProcessResult(context, tmpOut, tmpErr, process));
        } catch(IOException e) {
            error(context, e);
        }
    }

    private static void sendProcessResult(RoutingContext context, File stdout, File stderr, Process process) {
        try(var outStream = new FileInputStream(stdout); var errStream = new FileInputStream(stderr)) {
            var json = new JsonObject()
                    .put("exitCode", process.exitValue())
                    .put("stdout", cleanOutput(toUTF8(outStream)))
                    .put("stderr", toUTF8(errStream));
            context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(json.toBuffer());
        } catch(IOException e) {
            error(context, e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            stdout.delete();
            //noinspection ResultOfMethodCallIgnored
            stderr.delete();
        }
    }

    private static String toUTF8(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void badRequest(RoutingContext context, String message) {
        context.response()
                .setStatusCode(400)
                .setStatusMessage(message)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", true).put("message", message).toBuffer());
    }

    private static void error(RoutingContext context, Throwable t) {
        context.response()
                .setStatusCode(500)
                .setStatusMessage("Internal Server Error")
                .putHeader("Content-Type", "application/json")
                .end(RequestUtils.encodeThrowable(context, t).toBuffer());
    }

    private static String cleanOutput(String stdout) {
        String[] parts = stdout.split("\n");
        if(parts.length < 2) return stdout;
        if(!parts[0].strip().equalsIgnoreCase("Connected to remote JVM")) {
            return stdout;
        }
        if(!parts[1].strip().equalsIgnoreCase("Response code = 0")) {
            return stdout;
        }
        return Arrays.stream(parts).skip(2).collect(Collectors.joining("\n"));
    }
}
