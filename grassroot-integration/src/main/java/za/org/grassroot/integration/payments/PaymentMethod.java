package za.org.grassroot.integration.payments;

/**
 * Created by luke on 2016/10/27.
 */
public class PaymentMethod {

    private String cardHolder;
    private String cardNumber; // both for placeholder, and for flexibility in future (e.g., intelligent handling of "-" separators)
    private String cardBrand;
    private int expiryMonth;
    private int expiryYear;
    private int securityCode;

    public static class Builder {
        private String cardHolder;
        private String cardNumber;
        private String cardBrand;
        private int expiryMonth;
        private int expiryYear;
        private int securityCode;

        public Builder(String cardHolder) {
            this.cardHolder = cardHolder;
        }

        public Builder cardNumber(String cardNumber) {
            this.cardNumber = cardNumber;
            return this;
        }

        public Builder cardBrand(String cardBrand) {
            this.cardBrand = cardBrand;
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

    // if can work out at some point how to make th:field work, bring back these placeholders
    public static PaymentMethod makeEmpty() {
        PaymentMethod method = new PaymentMethod();
        //method.cardHolder = "Card holder's name";
        //method.cardNumber = "Credit/Debit card number";
        return method;
    }

    private PaymentMethod() {
        // for make empty (in turn for empty form bindings)
    }

    private PaymentMethod(String cardHolder) {
        this.cardHolder = cardHolder;
    }

    // these need to be public for the template form binding
    public String getCardHolder() {
        return cardHolder;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getCardBrand() { return cardBrand; }

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

    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }

    public void setCardNumber(String cardNumber) {
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
