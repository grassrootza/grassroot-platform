package za.org.grassroot.integration.email;

/**
 * Created by luke on 2016/10/24.
 */
public class GrassrootEmail {

    // todo : make these all work

    private final String from;
    private final String address;
    private final String subject;
    private final String content;

    public static class EmailBuilder {
        private String address;
        private String from;
        private String subject;
        private String content;

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

        public EmailBuilder address(String address) {
            this.address = address;
            return this;
        }

        public GrassrootEmail build() {
            return new GrassrootEmail(this.from, this.address, this.subject, this.content);
        }
    }

    private GrassrootEmail(String from, String address, String subject, String content) {
        this.from = from;
        this.address = address;
        this.subject = subject;
        this.content = content;
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
}
