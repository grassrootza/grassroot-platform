package za.org.grassroot.integration.xmpp;


import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.xmpp.config.XmppConnectionFactoryBean;
import org.springframework.integration.xmpp.outbound.ChatMessageSendingMessageHandler;
import org.springframework.messaging.MessageChannel;
import za.org.grassroot.integration.config.InfrastructureConfiguration;

import javax.net.ssl.SSLSocketFactory;

/**
 * Created by paballo on 2016/03/31.
 */
@Configuration
@Import(InfrastructureConfiguration.class)
public class XMPPConfiguration {

    private String host = "gcm-xmpp.googleapis.com";
    private int port = 5235;

    private Logger log = LoggerFactory.getLogger(XMPPConfiguration.class);

    @Bean
    public ConnectionConfiguration connectionConfiguration() {
        ConnectionConfiguration connectionConfiguration = new ConnectionConfiguration(host, port);
        connectionConfiguration.setCompressionEnabled(false);
        connectionConfiguration.setSocketFactory(SSLSocketFactory.getDefault());
        connectionConfiguration.setSecurityMode(ConnectionConfiguration.SecurityMode.enabled);
        connectionConfiguration.setReconnectionAllowed(true);
        connectionConfiguration.setRosterLoadedAtLogin(false);
        return connectionConfiguration;

    }

    @Bean(name = "gcmConnection")
    public XmppConnectionFactoryBean xmppConnectionFactoryBean() {
        log.info("Starting up XMPP connection");
        XmppConnectionFactoryBean connectionFactoryBean = new XmppConnectionFactoryBean(connectionConfiguration());
        connectionFactoryBean.setUser(System.getenv("GCM_SENDER_ID"));
        connectionFactoryBean.setPassword(System.getenv("GCM_KEY"));
        connectionFactoryBean.setAutoStartup(true);
        log.info("XMPP connection succesfully started up");
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
    @ServiceActivator(inputChannel = "gcmOutBoundChannel")
    public ChatMessageSendingMessageHandler chatMessageSendingMessageHandler(XMPPConnection connection){
        ChatMessageSendingMessageHandler adapter = new ChatMessageSendingMessageHandler(connection);
        return adapter;

    }





}
