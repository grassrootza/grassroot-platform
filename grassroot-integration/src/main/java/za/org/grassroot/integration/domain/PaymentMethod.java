package za.org.grassroot.integration.domain;

/**
 * Created by luke on 2016/10/27.
 */
public class PaymentMethod {

    private String cardHolder;
    private long cardNumber;
    private int expiryMonth;
    private int expiryYear;
    private int securityCode;

    public static class Builder {
        private String cardHolder;
        private long cardNumber;
        private int expiryMonth;
        private int expiryYear;
        private int securityCode;

        public Builder(String cardHolder) {
            this.cardHolder = cardHolder;
        }

        public Builder cardNumber(long cardNumber) {
            this.cardNumber = cardNumber;
            return this;
        }

        public Builder expiryMonth(int expiryMonth) {
            this.expiryMonth = expiryMonth;
            return this;
        }

        public Builder expiryYear(int expiryYear) {
            this.expiryYear = expiryYear;
            return this;
        }

        public Builder securityCode(int securityCode) {
            this.securityCode = securityCode;
            return this;
        }

        public PaymentMethod build() {
            PaymentMethod method = new PaymentMethod(cardHolder);
            method.cardNumber = cardNumber;
            method.expiryMonth = expiryMonth;
            method.expiryYear = expiryYear;
            method.securityCode = securityCode;
            return method;
        }
    }

    public static PaymentMethod makeEmpty() {
        return new PaymentMethod();
    }

    private PaymentMethod() {
        // for make empty (in turn for empty form bindings)
    }

    private PaymentMethod(String cardHolder) {
        this.cardHolder = cardHolder;
    }

    public String getCardHolder() {
        return cardHolder;
    }

    public long getCardNumber() {
        return cardNumber;
    }

    public int getExpiryMonth() {
        return expiryMonth;
    }

    public int getExpiryYear() {
        return expiryYear;
    }

    public int getSecurityCode() {
        return securityCode;
    }

    public void setCardHolder(String cardHolder) {
        this.cardHolder = cardHolder;
    }

    public void setCardNumber(long cardNumber) {
        this.cardNumber = cardNumber;
    }

    public void setExpiryMonth(int expiryMonth) {
        this.expiryMonth = expiryMonth;
    }

    public void setExpiryYear(int expiryYear) {
        this.expiryYear = expiryYear;
    }

    public void setSecurityCode(int securityCode) {
        this.securityCode = securityCode;
    }
}
