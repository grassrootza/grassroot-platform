
package za.org.grassroot.integration.location.aatmodels;

import javax.annotation.Generated;
import javax.xml.bind.annotation.*;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="AddAllowedMsisdnResult" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;any/>
 *                 &lt;/sequence>
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
    "addAllowedMsisdnResult"
})
@XmlRootElement(name = "AddAllowedMsisdnResponse", namespace = "http://lbs.gsm.co.za/")
@Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
public class AddAllowedMsisdnResponse {

    @XmlElement(name = "AddAllowedMsisdnResult", namespace = "http://lbs.gsm.co.za/")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected AddAllowedMsisdnResponse.AddAllowedMsisdnResult addAllowedMsisdnResult;

    /**
     * Gets the value of the addAllowedMsisdnResult property.
     * 
     * @return
     *     possible object is
     *     {@link AddAllowedMsisdnResponse.AddAllowedMsisdnResult }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public AddAllowedMsisdnResponse.AddAllowedMsisdnResult getAddAllowedMsisdnResult() {
        return addAllowedMsisdnResult;
    }

    /**
     * Sets the value of the addAllowedMsisdnResult property.
     * 
     * @param value
     *     allowed object is
     *     {@link AddAllowedMsisdnResponse.AddAllowedMsisdnResult }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setAddAllowedMsisdnResult(AddAllowedMsisdnResponse.AddAllowedMsisdnResult value) {
        this.addAllowedMsisdnResult = value;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "addAllowedMsisdn"
    })
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public static class AddAllowedMsisdnResult {

        @XmlElement(name = "addAllowedMsisdn")
        protected AddAllowedMsisdnResultResponse addAllowedMsisdn;

        public AddAllowedMsisdnResultResponse getAddAllowedMsisdn() {
            return addAllowedMsisdn;
        }

        public void setAddAllowedMsisdn(AddAllowedMsisdnResultResponse addAllowedMsisdn) {
            this.addAllowedMsisdn = addAllowedMsisdn;
        }
    }

}
