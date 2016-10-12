package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.xmpp.inbound.ChatMessageListeningEndpoint;
import org.springframework.integration.xmpp.support.DefaultXmppHeaderMapper;
import org.springframework.integration.xmpp.support.XmppHeaderMapper;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import za.org.grassroot.integration.domain.GcmUpstreamMessage;

import java.io.IOException;
import java.util.Map;

/**
 * Created by paballo on 2016/04/04.
 */
public class GcmMessageListeningEndpoint extends ChatMessageListeningEndpoint {

    private static final Logger log = LoggerFactory.getLogger(GcmMessageListeningEndpoint.class);

    private StanzaListener stanzaListener = new GcmPacketListener();
    private XmppHeaderMapper headerMapper = new DefaultXmppHeaderMapper();
    private ObjectMapper mapper = new ObjectMapper();

    public GcmMessageListeningEndpoint(XMPPConnection connection) {
        super(connection);
        ProviderManager.addExtensionProvider(GcmPacketExtension.GCM_ELEMENT_NAME, GcmPacketExtension.GCM_NAMESPACE,
                new ExtensionElementProvider<ExtensionElement>() {
                    @Override
                    public ExtensionElement parse(XmlPullParser parser, int initialDepth) throws XmlPullParserException, IOException, SmackException {
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
        this.xmppConnection.addAsyncStanzaListener(this.stanzaListener, null);
    }

    @Override
    protected void doStop() {
        if (this.xmppConnection != null) {
            this.xmppConnection.removeAsyncStanzaListener(this.stanzaListener);
        }
    }

    private class GcmPacketListener implements StanzaListener {
        @Override
        public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
            log.debug("Packet received from gcm " + packet.toString());
            if (packet instanceof org.jivesoftware.smack.packet.Message) {
                org.jivesoftware.smack.packet.Message xmppMessage = (org.jivesoftware.smack.packet.Message) packet;
                final GcmPacketExtension gcmExtension = (GcmPacketExtension)xmppMessage.getExtension(GcmPacketExtension.GCM_NAMESPACE);
                try {
                    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                    final GcmUpstreamMessage entity = mapper.readValue(gcmExtension.getJson(), GcmUpstreamMessage.class);
                    log.info("Relaying chat message : {}", entity.toString());
                    Map<String, Object> mappedHeaders = headerMapper.toHeadersFromRequest(xmppMessage);
                    sendMessage(MessageBuilder.withPayload(entity).copyHeaders(mappedHeaders).build());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                log.warn("Unsupported Packet " + packet.toString());
            }

        }

    }
}
