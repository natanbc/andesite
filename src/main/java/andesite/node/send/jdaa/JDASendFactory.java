package andesite.node.send.jdaa;

import net.dv8tion.jda.api.audio.factory.IAudioSendFactory;
import net.dv8tion.jda.api.audio.factory.IAudioSendSystem;
import net.dv8tion.jda.api.audio.factory.IPacketProvider;

public class JDASendFactory implements IAudioSendFactory {
    @Override
    public IAudioSendSystem createSendSystem(IPacketProvider packetProvider) {
        return new JDASendSystem(packetProvider);
    }
}
