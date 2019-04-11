package example;

import andesite.node.NodeState;
import andesite.node.Plugin;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.Nonnull;

public class ExamplePlugin implements Plugin {
    @Override
    public void init(@Nonnull NodeState state) {
        //defaults are in src/main/resources/reference.conf
        System.out.println(state.config().getString("example.key"));
        System.out.println(state.config().getString("example.otherKey"));
    }
    
    @Override
    public void configureRouter(@Nonnull NodeState state, @Nonnull Router router) {
        router.get("/test").handler(r -> r.response().end("Hello world"));
    }

    @Nonnull
    @Override
    public HookResult onRawHttpRequest(@Nonnull NodeState state, @Nonnull RoutingContext context) {
        System.out.println(context.request().rawMethod() + " " + context.request().path());
        return HookResult.CALL_NEXT;
    }
}
