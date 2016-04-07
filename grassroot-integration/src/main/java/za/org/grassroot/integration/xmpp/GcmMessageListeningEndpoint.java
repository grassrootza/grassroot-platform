package za.org.grassroot.integration.xmpp;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.gcm.packet.GcmPacketExtension;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.xmpp.inbound.ChatMessageListeningEndpoint;
import org.springframework.integration.xmpp.support.DefaultXmppHeaderMapper;
import org.springframework.integration.xmpp.support.XmppHeaderMapper;
import org.xmlpull.v1.XmlPullParser;

import java.util.Map;

/**
 * Created by paballo on 2016/04/04.
 */
public class GcmMessageListeningEndpoint extends ChatMessageListeningEndpoint {

    protected PacketListener packetListener = new GcmPacketListener();
    protected XmppHeaderMapper headerMapper = new DefaultXmppHeaderMapper();

    public GcmMessageListeningEndpoint(XMPPConnection connection) {
        super(connection);
        ProviderManager.addExtensionProvider(za.org.grassroot.integration.xmpp.GcmPacketExtension.GCM_ELEMENT_NAME,
                za.org.grassroot.integration.xmpp.GcmPacketExtension.GCM_NAMESPACE,
                new PacketExtensionProvider() {
                    @Override
                    public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
                        String json = parser.nextText();
                        return new GcmPacketExtension(json);
                    }

                });
    }
    @Override
    public void setHeaderMapper(XmppHeaderMapper headerMapper) {
        super.setHeaderMapper(headerMapper);
        this.headerMapper = headerMapper;
    }

    @Override
    protected void doStart() {
        this.xmppConnection.addPacketListener(this.packetListener, null);
    }

    @Override
    protected void doStop() {
        if (this.xmppConnection != null) {
            this.xmppConnection.removePacketListener(this.packetListener);
        }
    }
   private class GcmPacketListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) throws SmackException.NotConnectedException {
                org.jivesoftware.smack.packet.Message xmppMessage = (org.jivesoftware.smack.packet.Message) packet;
                Map<String, Object> mappedHeaders = headerMapper.toHeadersFromRequest(xmppMessage);
                sendMessage(MessageBuilder.withPayload(xmppMessage).copyHeaders(mappedHeaders).build());

        }

    }

}
