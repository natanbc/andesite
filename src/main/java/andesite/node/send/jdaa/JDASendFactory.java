package andesite.node.send.jdaa;

import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import net.dv8tion.jda.core.audio.factory.IAudioSendSystem;
import net.dv8tion.jda.core.audio.factory.IPacketProvider;

public class JDASendFactory implements IAudioSendFactory {
    @Override
    public IAudioSendSystem createSendSystem(IPacketProvider packetProvider) {
        return new JDASendSystem(packetProvider);
    }
}
