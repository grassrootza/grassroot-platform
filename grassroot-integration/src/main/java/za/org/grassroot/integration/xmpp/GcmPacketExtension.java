package za.org.grassroot.integration.xmpp;

/**
 * Created by paballo on 2016/04/04.
 */

import org.jivesoftware.smack.packet.DefaultExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

public class GcmPacketExtension extends DefaultExtensionElement {

    protected static final String GCM_ELEMENT_NAME = "gcm";
    protected static final String GCM_NAMESPACE = "google:mobile:data";
    private final String json;

    public GcmPacketExtension(String json) {
        super(GCM_ELEMENT_NAME, GCM_NAMESPACE);
        this.json = json;
    }

    public String getJson() {
        return json;
    }

    @Override
    public CharSequence toXML() {
        XmlStringBuilder buffer = new XmlStringBuilder();
        final String elementName = getElementName();
        buffer.halfOpenElement(elementName).xmlnsAttribute(getNamespace()).rightAngleBracket();
        buffer.append(json);
        buffer.closeElement(elementName);
        return buffer;
    }

}
