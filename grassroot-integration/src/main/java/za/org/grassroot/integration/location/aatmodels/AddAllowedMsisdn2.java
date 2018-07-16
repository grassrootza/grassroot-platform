
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
 *         &lt;element name="username" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="password" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="msisdn" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="permissionType" type="{http://www.w3.org/2001/XMLSchema}int"/>
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
    "username",
    "password",
    "msisdn",
    "permissionType"
})
@XmlRootElement(name = "AddAllowedMsisdn2", namespace = "http://lbs.gsm.co.za/")
@Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
public class AddAllowedMsisdn2 {

    @XmlElement(namespace = "http://lbs.gsm.co.za/")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String username;
    @XmlElement(namespace = "http://lbs.gsm.co.za/")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String password;
    @XmlElement(namespace = "http://lbs.gsm.co.za/")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String msisdn;
    @XmlElement(namespace = "http://lbs.gsm.co.za/")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected int permissionType;

    /**
     * Gets the value of the username property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getUsername() {
        return username;
    }

    /**
     * Sets the value of the username property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setUsername(String value) {
        this.username = value;
    }

    /**
     * Gets the value of the password property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getPassword() {
        return password;
    }

    /**
     * Sets the value of the password property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setPassword(String value) {
        this.password = value;
    }

    /**
     * Gets the value of the msisdn property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getMsisdn() {
        return msisdn;
    }

    /**
     * Sets the value of the msisdn property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setMsisdn(String value) {
        this.msisdn = value;
    }

    /**
     * Gets the value of the permissionType property.
     * 
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public int getPermissionType() {
        return permissionType;
    }

    /**
     * Sets the value of the permissionType property.
     * 
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setPermissionType(int value) {
        this.permissionType = value;
    }

}
