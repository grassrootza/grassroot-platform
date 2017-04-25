
package za.org.grassroot.integration.location.aatmodels;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


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
 *         &lt;element name="GetLocationResult" minOccurs="0">
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
    "getLocationResult"
})
@XmlRootElement(name = "GetLocationResponse", namespace = "http://lbs.gsm.co.za/")
@Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
public class GetLocationResponse {

    @XmlElement(name = "GetLocationResult", namespace = "http://lbs.gsm.co.za/")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected GetLocationResponse.GetLocationResult getLocationResult;

    /**
     * Gets the value of the getLocationResult property.
     * 
     * @return
     *     possible object is
     *     {@link GetLocationResponse.GetLocationResult }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public GetLocationResponse.GetLocationResult getGetLocationResult() {
        return getLocationResult;
    }

    /**
     * Sets the value of the getLocationResult property.
     * 
     * @param value
     *     allowed object is
     *     {@link GetLocationResponse.GetLocationResult }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setGetLocationResult(GetLocationResponse.GetLocationResult value) {
        this.getLocationResult = value;
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
     *       &lt;sequence>
     *         &lt;any/>
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
        "content"
    })
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public static class GetLocationResult {

        @XmlMixed
        @XmlAnyElement(lax = true)
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
        protected List<Object> content;

        /**
         * Gets the value of the content property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the content property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getContent().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link Object }
         * {@link String }
         * 
         * 
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2017-04-25T10:47:58+09:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public List<Object> getContent() {
            if (content == null) {
                content = new ArrayList<Object>();
            }
            return this.content;
        }

    }

}
