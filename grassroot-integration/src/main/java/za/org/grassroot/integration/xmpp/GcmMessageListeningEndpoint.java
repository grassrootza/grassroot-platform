package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.xmpp.inbound.ChatMessageListeningEndpoint;
import org.springframework.integration.xmpp.support.DefaultXmppHeaderMapper;
import org.springframework.integration.xmpp.support.XmppHeaderMapper;
import org.xmlpull.v1.XmlPullParser;
import za.org.grassroot.integration.domain.GcmEntity;
import za.org.grassroot.integration.domain.GcmUpstreamMessage;

import java.io.IOException;
import java.util.Map;

/**
 * Created by paballo on 2016/04/04.
 */
public class GcmMessageListeningEndpoint extends ChatMessageListeningEndpoint {


    private Logger log = Logger.getLogger(GcmMessageListeningEndpoint.class);
    protected PacketListener packetListener = new GcmPacketListener();
    protected XmppHeaderMapper headerMapper = new DefaultXmppHeaderMapper();
    private ObjectMapper mapper = new ObjectMapper();

    public GcmMessageListeningEndpoint(XMPPConnection connection) {
        super(connection);
        ProviderManager.addExtensionProvider(GcmPacketExtension.GCM_ELEMENT_NAME, GcmPacketExtension.GCM_NAMESPACE,
                new PacketExtensionProvider() {
                    @Override

                    public PacketExtension parseExtension(XmlPullParser parser) throws Exception {

                        String json = parser.nextText();

                        return new GcmPacketExtension(json);

                    }

                });

    }

    public String getComponentType() {
        return "xmpp:inbound-channel-adapter-gcm";
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

            log.info("Packet received from gcm " + packet.toString());
            if (packet instanceof org.jivesoftware.smack.packet.Message) {
                org.jivesoftware.smack.packet.Message xmppMessage = (org.jivesoftware.smack.packet.Message) packet;
                final GcmPacketExtension gcmExtension = (GcmPacketExtension)xmppMessage.getExtension(GcmPacketExtension.GCM_NAMESPACE);
                log.info("Message received: " + xmppMessage.toXML().toString());
                try {
                    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                    final GcmUpstreamMessage entity = mapper.readValue(gcmExtension.getJson(), GcmUpstreamMessage.class);
                    Map<String, Object> mappedHeaders = headerMapper.toHeadersFromRequest(xmppMessage);
                    sendMessage(MessageBuilder.withPayload(entity).copyHeaders(mappedHeaders).build());
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {

                log.warn("Unsuported Packet " + packet.toString());
            }

        }

    }
}
