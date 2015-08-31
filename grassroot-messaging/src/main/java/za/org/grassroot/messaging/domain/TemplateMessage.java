package za.org.grassroot.messaging.domain;

/**
 * @author Lesetse Kimwaga
 */
public class TemplateMessage extends Message {

    private String templateId;
    private Object data;

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public TemplateMessage withData(String templateId, String data) {
        this.templateId = templateId;
        this.data = data;
        return this;
    }
}
