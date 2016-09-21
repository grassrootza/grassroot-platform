package za.org.grassroot.integration.sms.aat;

import javax.annotation.Generated;
import javax.xml.bind.annotation.*;

/**
 * <p>Java class to interpret response XML from AAT SMS gateway (based on generated xsd)
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="submitresult">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;attribute name="action" type="{http://www.w3.org/2001/XMLSchema}string" />
 *                 &lt;attribute name="error" type="{http://www.w3.org/2001/XMLSchema}int" />
 *                 &lt;attribute name="key" type="{http://www.w3.org/2001/XMLSchema}int" />
 *                 &lt;attribute name="result" type="{http://www.w3.org/2001/XMLSchema}int" />
 *                 &lt;attribute name="number" type="{http://www.w3.org/2001/XMLSchema}string" />
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "submitresult"
})
@XmlRootElement(name = "aatsms")
@Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
public class AatSmsResponse {

    @XmlElement(required = true)
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected AatSmsResponse.Submitresult submitresult;

    /**
     * Gets the value of the submitresult property.
     * 
     * @return
     *     possible object is
     *     {@link AatSmsResponse.Submitresult }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public AatSmsResponse.Submitresult getSubmitresult() {
        return submitresult;
    }

    /**
     * Sets the value of the submitresult property.
     * 
     * @param value
     *     allowed object is
     *     {@link AatSmsResponse.Submitresult }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setSubmitresult(AatSmsResponse.Submitresult value) {
        this.submitresult = value;
    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;attribute name="action" type="{http://www.w3.org/2001/XMLSchema}string" />
     *       &lt;attribute name="error" type="{http://www.w3.org/2001/XMLSchema}string" />
     *       &lt;attribute name="key" type="{http://www.w3.org/2001/XMLSchema}int" />
     *       &lt;attribute name="result" type="{http://www.w3.org/2001/XMLSchema}int" />
     *       &lt;attribute name="number" type="{http://www.w3.org/2001/XMLSchema}string" />
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public static class Submitresult {

        @XmlAttribute(name = "action")
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        protected String action;
        @XmlAttribute(name = "error")
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        protected Integer error;
        @XmlAttribute(name = "key")
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        protected Integer key;
        @XmlAttribute(name = "result")
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        protected Integer result;
        @XmlAttribute(name = "number")
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        protected String number;

        /**
         * Gets the value of the action property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public String getAction() {
            return action;
        }

        /**
         * Sets the value of the action property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public void setAction(String value) {
            this.action = value;
        }

        /**
         * Gets the value of the error property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public Integer getError() {
            return error;
        }

        /**
         * Sets the value of the error property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public void setError(Integer value) {
            this.error = value;
        }

        /**
         * Gets the value of the key property.
         * 
         * @return
         *     possible object is
         *     {@link Integer }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public Integer getKey() {
            return key;
        }

        /**
         * Sets the value of the key property.
         * 
         * @param value
         *     allowed object is
         *     {@link Integer }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public void setKey(Integer value) {
            this.key = value;
        }

        /**
         * Gets the value of the result property.
         * 
         * @return
         *     possible object is
         *     {@link Integer }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public Integer getResult() {
            return result;
        }

        /**
         * Sets the value of the result property.
         * 
         * @param value
         *     allowed object is
         *     {@link Integer }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public void setResult(Integer value) {
            this.result = value;
        }

        /**
         * Gets the value of the number property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public String getNumber() {
            return number;
        }

        /**
         * Sets the value of the number property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2016-09-19T03:46:30+02:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public void setNumber(String value) {
            this.number = value;
        }

        @Override
        public String toString() {
            return "Submitresult{" +
                    "action='" + action + '\'' +
                    ", error='" + error + '\'' +
                    ", key=" + key +
                    ", result=" + result +
                    ", number='" + number + '\'' +
                    '}';
        }
    }

}
