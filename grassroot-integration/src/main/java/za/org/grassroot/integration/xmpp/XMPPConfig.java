package za.org.grassroot.integration.xmpp;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.xmpp.config.XmppConnectionFactoryBean;
import org.springframework.integration.xmpp.outbound.ChatMessageSendingMessageHandler;
import org.springframework.messaging.MessageChannel;
import za.org.grassroot.integration.InfrastructureConfiguration;

import javax.net.ssl.SSLSocketFactory;

/**
 * Created by paballo on 2016/03/31.
 */
@Configuration
@Import(InfrastructureConfiguration.class)
@ConditionalOnProperty(name = "gcm.connection.enabled", havingValue = "true",  matchIfMissing = false)
public class XMPPConfig {

    @Value("${gcm.connection.url}")
    private String host;

    @Value("${gcm.connection.port}")
    private int port;

    @Value("${gcm.sender.id}")
    private String gcmSenderId;

    @Value("${gcm.sender.key}")
    private String gcmSenderKey;

    private Logger log = LoggerFactory.getLogger(XMPPConfig.class);

    private XMPPTCPConnectionConfiguration connectionConfiguration() {
        return XMPPTCPConnectionConfiguration
                .builder()
                .setServiceName(host)
                .setCompressionEnabled(true)
                .setHost(host)
                .setPort(port)
                .setUsernameAndPassword(gcmSenderId, gcmSenderKey)
                .setSocketFactory(SSLSocketFactory.getDefault())
                .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
                .build();
    }

    @Bean(name = "gcmConnection")
    public XmppConnectionFactoryBean xmppConnectionFactoryBean() {
        log.info("Starting up XMPP connection, for URL={} on port={}, with ID={} and key={}", host, port, gcmSenderId, gcmSenderKey);
        XmppConnectionFactoryBean connectionFactoryBean = new XmppConnectionFactoryBean();
        connectionFactoryBean.setConnectionConfiguration(connectionConfiguration());
        connectionFactoryBean.setAutoStartup(true);
        Roster.setRosterLoadedAtLoginDefault(false);
        log.info("XMPP connection successfully started up");
        return connectionFactoryBean;
    }

    @Bean
    public GcmMessageListeningEndpoint inboundAdapter(XMPPConnection connection, MessageChannel gcmInboundChannel) {
        GcmMessageListeningEndpoint endpoint = new GcmMessageListeningEndpoint(connection);
        endpoint.setOutputChannel(gcmInboundChannel);
        endpoint.setAutoStartup(true);
        return endpoint;
    }

    @Bean
    @ServiceActivator(inputChannel = "gcmXmppOutboundChannel")
    public ChatMessageSendingMessageHandler chatMessageSendingMessageHandler(XMPPConnection connection){
        ChatMessageSendingMessageHandler adapter = new ChatMessageSendingMessageHandler(connection);
        return adapter;
    }
}