package za.org.grassroot.integration.xmpp;

import org.apache.log4j.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Packet;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.xmpp.inbound.ChatMessageListeningEndpoint;
import org.springframework.integration.xmpp.support.DefaultXmppHeaderMapper;
import org.springframework.integration.xmpp.support.XmppHeaderMapper;

import java.util.Map;

/**
 * Created by paballo on 2016/04/04.
 */
public class GcmMessageListeningEndpoint extends ChatMessageListeningEndpoint {


    private Logger log = Logger.getLogger(GcmMessageListeningEndpoint.class);
    protected PacketListener packetListener = new GcmPacketListener();
    protected XmppHeaderMapper headerMapper = new DefaultXmppHeaderMapper();

    public GcmMessageListeningEndpoint(XMPPConnection connection) {
        super(connection);

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
                log.info("Message received: " + xmppMessage.toXML().toString());
                Map<String, Object> mappedHeaders = headerMapper.toHeadersFromRequest(xmppMessage);
                sendMessage(MessageBuilder.withPayload(xmppMessage).copyHeaders(mappedHeaders).build());
            } else {

                log.warn("Unsuported Packet " + packet.toString());
            }

        }

    }
}
