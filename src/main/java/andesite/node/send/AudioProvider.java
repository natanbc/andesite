package andesite.node.send;

import java.nio.ByteBuffer;

public interface AudioProvider {
    boolean canProvide();

    ByteBuffer provide();
}
