package za.org.grassroot.integration.messaging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.io.File;

/**
 * Created by luke on 2016/10/24.
 */
@Getter
public class GrassrootEmail {

    private final String from;
    private final String address;
    private final String subject;
    private final String content;
    private final String htmlContent;

    @Setter private String fromAddress;
    @Setter private File attachment;
    @Setter private String attachmentName;

    public static class EmailBuilder {
        private String address;
        private String from;
        private String fromAddress;
        private String subject;
        private String content;
        private String htmlContent;
        private File attachment;
        private String attachmentName;

        public EmailBuilder() { }

        public EmailBuilder(String subject) {
            this.subject = subject;
        }

        public EmailBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public EmailBuilder from(String from) {
            this.from = from;
            return this;
        }

        public EmailBuilder fromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
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
            GrassrootEmail email = new GrassrootEmail(this.from, this.fromAddress, this.address, this.subject, this.content, this.htmlContent);
            if (this.attachment != null) {
                email.attachment = this.attachment;
                email.attachmentName = this.attachmentName;
            }
            return email;
        }
    }

    private GrassrootEmail(String from, String fromAddress, String address, String subject, String content, String htmlContent) {
        this.from = from;
        this.fromAddress = fromAddress;
        this.address = address;
        this.subject = subject;
        this.content = content;
        this.htmlContent = htmlContent;
    }

    public boolean hasHtmlContent() { return !StringUtils.isEmpty(htmlContent); }

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
