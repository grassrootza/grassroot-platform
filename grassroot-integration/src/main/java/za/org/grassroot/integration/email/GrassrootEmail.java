package za.org.grassroot.integration.email;

import org.springframework.util.StringUtils;

import java.io.File;

/**
 * Created by luke on 2016/10/24.
 */
public class GrassrootEmail {

    private final String from;
    private final String address;
    private final String subject;
    private final String content;
    private final String htmlContent;

    private File attachment;
    private String attachmentName;

    public static class EmailBuilder {
        private String address;
        private String from;
        private String subject;
        private String content;
        private String htmlContent;
        private File attachment;
        private String attachmentName;

        public EmailBuilder(String subject) {
            this.subject = subject;
        }

        public EmailBuilder from(String from) {
            this.from = from;
            return this;
        }

        public EmailBuilder content(String content) {
            this.content = content;
            return this;
        }

        public EmailBuilder htmlContent(String htmlContent) {
            this.htmlContent = htmlContent;
            return this;
        }

        public EmailBuilder address(String address) {
            this.address = address;
            return this;
        }

        public EmailBuilder attachment(String attachmentName, File attachment) {
            this.attachmentName = attachmentName;
            this.attachment = attachment;
            return this;
        }

        public GrassrootEmail build() {
            GrassrootEmail email = new GrassrootEmail(this.from, this.address, this.subject, this.content, this.htmlContent);
            if (this.attachment != null) {
                email.attachment = this.attachment;
                email.attachmentName = this.attachmentName;
            }
            return email;
        }
    }

    private GrassrootEmail(String from, String address, String subject, String content, String htmlContent) {
        this.from = from;
        this.address = address;
        this.subject = subject;
        this.content = content;
        this.htmlContent = htmlContent;
    }

    public String getFrom() {
        return from;
    }

    public String getAddress() {
        return address;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public String getHtmlContent() { return htmlContent; }

    public boolean hasHtmlContent() { return !StringUtils.isEmpty(htmlContent); }

    public File getAttachment() { return attachment; }

    public String getAttachmentName() { return attachmentName; }

    public boolean hasAttachment() {
        return attachment != null;
    }

    @Override
    public String toString() {
        return "GrassrootEmail{" +
                "from='" + from + '\'' +
                ", address='" + address + '\'' +
                ", subject='" + subject + '\'' +
                ", content='" + content + '\'' +
                ", attachmentName='" + attachmentName + '\'' +
                '}';
    }
}
