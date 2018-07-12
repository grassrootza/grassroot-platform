
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
 *         &lt;element name="AddAllowedMsisdn2Result" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
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
    "addAllowedMsisdn2Result"
})
@XmlRootElement(name = "AddAllowedMsisdn2Response", namespace = "http://lbs.gsm.co.za/")
@Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
public class AddAllowedMsisdn2Response {

    @XmlElement(name = "AddAllowedMsisdn2Result", namespace = "http://lbs.gsm.co.za/")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String addAllowedMsisdn2Result;

    /**
     * Gets the value of the addAllowedMsisdn2Result property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getAddAllowedMsisdn2Result() {
        return addAllowedMsisdn2Result;
    }

    /**
     * Sets the value of the addAllowedMsisdn2Result property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setAddAllowedMsisdn2Result(String value) {
        this.addAllowedMsisdn2Result = value;
    }

}
