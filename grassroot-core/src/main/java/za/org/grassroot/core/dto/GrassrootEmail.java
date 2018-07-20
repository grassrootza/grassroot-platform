package za.org.grassroot.core.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.util.GrassrootTemplate;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by luke on 2016/10/24.
 */
@Getter @Setter @NoArgsConstructor @Slf4j
public class GrassrootEmail implements GrassrootTemplate {

    private String fromName;
    private String fromAddress;

    private String toAddress;
    private String toName;

    private Set<String> toUserUids;

    private String subject;

    private String content;
    private String htmlContent;
    private boolean hasTemplateFields;

    private File attachment;
    private String attachmentName;

    private String messageId;
    private String baseId; // for tracking things like broadcast ID
    private String groupUid; // for unsubscribes

    private Map<String, String> attachmentUidsAndNames;

    public static class EmailBuilder {
        private String toAddress;
        private String toName;
        private Set<String> toUserUids;

        private String fromName;
        private String fromAddress;
        private String subject;
        private String content;
        private String htmlContent;
        private File attachment;
        private String attachmentName;

        private String messageId; // in case set by email
        private String baseId;

        @Getter private Map<String, String> attachmentUidsAndNames;

        public EmailBuilder() { }

        public EmailBuilder(String subject) {
            this.subject = subject;
        }

        public EmailBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public EmailBuilder fromName(String from) {
            this.fromName = from;
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

        public EmailBuilder toAddress(String address) {
            this.toAddress = address;
            return this;
        }

        public EmailBuilder toName(String name) {
            this.toName = name;
            return this;
        }

        public EmailBuilder toUserUids(Set<String> userUids) {
            this.toUserUids = userUids;
            return this;

        }

        public EmailBuilder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public EmailBuilder baseId(String baseId) {
            this.baseId = baseId;
            return this;
        }

        public EmailBuilder attachment(String attachmentName, File attachment) {
            this.attachmentName = attachmentName;
            this.attachment = attachment;
            return this;
        }

        public EmailBuilder attachmentRecordUids(List<String> attachmentUids) {
            if (attachmentUids != null) {
                if (this.attachmentUidsAndNames == null) {
                    this.attachmentUidsAndNames = new HashMap<>();
                }
                attachmentUids.forEach(uid -> this.attachmentUidsAndNames.put(uid, ""));
            }
            return this;
        }

        public EmailBuilder attachmentByKey(String attachmentName, String attachmentUid) {
            if (this.attachmentUidsAndNames == null) {
                this.attachmentUidsAndNames = new HashMap<>();
            }
            this.attachmentUidsAndNames.put(attachmentName, attachmentUid);
            return this;
        }

        public GrassrootEmail build() {
            GrassrootEmail email = new GrassrootEmail(this.fromName, this.fromAddress, this.toAddress, this.toName, this.toUserUids, this.subject, this.content, this.htmlContent);
            if (this.messageId != null) {
                email.messageId = this.messageId;
            }
            if (this.attachment != null) {
                email.attachment = this.attachment;
                email.attachmentName = this.attachmentName;
            }
            if (this.attachmentUidsAndNames != null) {
                email.attachmentUidsAndNames = this.attachmentUidsAndNames;
            }
            if (this.baseId != null) {
                email.baseId = this.baseId;
            }
            return email;
        }
    }

    public GrassrootEmail(String fromName, String fromAddress, String toAddress, String toName, Set<String> toUserUids, String subject, String content, String htmlContent) {
        this.fromName = fromName;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.toName = toName;
        this.toUserUids = toUserUids;
        this.subject = subject;
        this.content = content;
        this.htmlContent = htmlContent;
    }

    public GrassrootEmail copyIntoNew(String toAddress, String toName) {
        GrassrootEmail email = new GrassrootEmail(this.getFromName(), this.getFromAddress(),
                toAddress, toName, null, this.getSubject(), this.getContent(), this.getHtmlContent());
        email.setAttachment(this.getAttachment());
        email.setAttachmentUidsAndNames(this.getAttachmentUidsAndNames());
        email.setAttachmentName(toAddress);
        return email;
    }

    public Map<String, String> getAttachmentUidsAndNames() {
        return attachmentUidsAndNames == null ? new HashMap<>() : attachmentUidsAndNames;
    }

    public boolean isMultiUser() { return toUserUids != null && !toUserUids.isEmpty(); }

    public boolean hasHtmlContent() { return !StringUtils.isEmpty(htmlContent); }

    public boolean hasAttachment() {
        return attachment != null;
    }

    @Override
    public String toString() {
        return "GrassrootEmail{" +
                "from='" + fromName + '\'' +
                ", address='" + toAddress + '\'' +
                ", subject='" + subject + '\'' +
                ", content='" + content + '\'' +
                ", attachmentName='" + attachmentName + '\'' +
                '}';
    }
}
