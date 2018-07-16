
package za.org.grassroot.integration.location.aatmodels;

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
 *         &lt;element name="result">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="x" type="{http://www.w3.org/2001/XMLSchema}float"/>
 *                   &lt;element name="y" type="{http://www.w3.org/2001/XMLSchema}float"/>
 *                   &lt;element name="accuracy" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *                   &lt;element name="coordDate" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *                 &lt;/sequence>
 *                 &lt;attribute name="code" type="{http://www.w3.org/2001/XMLSchema}byte" />
 *                 &lt;attribute name="message" type="{http://www.w3.org/2001/XMLSchema}string" />
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
    "result"
})
@XmlRootElement(name = "getLocation")
public class GetLocationEntity {

    @XmlElement(required = true)
    protected GetLocationEntity.Result result;

    /**
     * Gets the value of the result property.
     * 
     * @return
     *     possible object is
     *     {@link GetLocationEntity.Result }
     *     
     */
    public GetLocationEntity.Result getResult() {
        return result;
    }

    /**
     * Sets the value of the result property.
     * 
     * @param value
     *     allowed object is
     *     {@link GetLocationEntity.Result }
     *     
     */
    public void setResult(GetLocationEntity.Result value) {
        this.result = value;
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
     *         &lt;element name="x" type="{http://www.w3.org/2001/XMLSchema}float"/>
     *         &lt;element name="y" type="{http://www.w3.org/2001/XMLSchema}float"/>
     *         &lt;element name="accuracy" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *         &lt;element name="coordDate" type="{http://www.w3.org/2001/XMLSchema}int"/>
     *       &lt;/sequence>
     *       &lt;attribute name="code" type="{http://www.w3.org/2001/XMLSchema}byte" />
     *       &lt;attribute name="message" type="{http://www.w3.org/2001/XMLSchema}string" />
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "x",
        "y",
        "accuracy",
        "coordDate"
    })
    public static class Result {

        protected float x;
        protected float y;
        @XmlElement(required = true)
        protected String accuracy;
        protected int coordDate;
        @XmlAttribute(name = "code")
        protected Byte code;
        @XmlAttribute(name = "message")
        protected String message;

        /**
         * Gets the value of the x property.
         * 
         */
        public float getX() {
            return x;
        }

        /**
         * Sets the value of the x property.
         * 
         */
        public void setX(float value) {
            this.x = value;
        }

        /**
         * Gets the value of the y property.
         * 
         */
        public float getY() {
            return y;
        }

        /**
         * Sets the value of the y property.
         * 
         */
        public void setY(float value) {
            this.y = value;
        }

        /**
         * Gets the value of the accuracy property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getAccuracy() {
            return accuracy;
        }

        /**
         * Sets the value of the accuracy property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setAccuracy(String value) {
            this.accuracy = value;
        }

        /**
         * Gets the value of the coordDate property.
         * 
         */
        public int getCoordDate() {
            return coordDate;
        }

        /**
         * Sets the value of the coordDate property.
         * 
         */
        public void setCoordDate(int value) {
            this.coordDate = value;
        }

        /**
         * Gets the value of the code property.
         * 
         * @return
         *     possible object is
         *     {@link Byte }
         *     
         */
        public Byte getCode() {
            return code;
        }

        /**
         * Sets the value of the code property.
         * 
         * @param value
         *     allowed object is
         *     {@link Byte }
         *     
         */
        public void setCode(Byte value) {
            this.code = value;
        }

        /**
         * Gets the value of the message property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getMessage() {
            return message;
        }

        /**
         * Sets the value of the message property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setMessage(String value) {
            this.message = value;
        }

    }

}
