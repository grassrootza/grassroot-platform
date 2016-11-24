
package za.org.grassroot.integration.payments;

import javax.xml.bind.annotation.*;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "transaction"
})
@XmlRootElement(name = "Response")
public class AsyncPaymentResponse {

    @XmlElement(name = "Transaction", required = true)
    protected AsyncPaymentResponse.Transaction transaction;
    @XmlAttribute(name = "version")
    protected BigDecimal version;

    public AsyncPaymentResponse.Transaction getTransaction() {
        return transaction;
    }
    public void setTransaction(AsyncPaymentResponse.Transaction value) {
        this.transaction = value;
    }

    public BigDecimal getVersion() {
        return version;
    }
    public void setVersion(BigDecimal value) {
        this.version = value;
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
     *         &lt;element name="Identification">
     *           &lt;complexType>
     *             &lt;complexContent>
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                 &lt;sequence>
     *                   &lt;element name="ShortID" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                   &lt;element name="UniqueID" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                 &lt;/sequence>
     *               &lt;/restriction>
     *             &lt;/complexContent>
     *           &lt;/complexType>
     *         &lt;/element>
     *         &lt;element name="Payment">
     *           &lt;complexType>
     *             &lt;complexContent>
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                 &lt;sequence>
     *                   &lt;element name="Clearing">
     *                     &lt;complexType>
     *                       &lt;complexContent>
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                           &lt;sequence>
     *                             &lt;element name="Amount" type="{http://www.w3.org/2001/XMLSchema}decimal"/>
     *                             &lt;element name="Currency" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                             &lt;element name="Descriptor" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                             &lt;element name="FxRate" type="{http://www.w3.org/2001/XMLSchema}decimal"/>
     *                             &lt;element name="FxSource" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                             &lt;element name="FxDate" type="{http://www.w3.org/2001/XMLSchema}dateTime"/>
     *                           &lt;/sequence>
     *                         &lt;/restriction>
     *                       &lt;/complexContent>
     *                     &lt;/complexType>
     *                   &lt;/element>
     *                 &lt;/sequence>
     *                 &lt;attribute name="code" type="{http://www.w3.org/2001/XMLSchema}string" />
     *               &lt;/restriction>
     *             &lt;/complexContent>
     *           &lt;/complexType>
     *         &lt;/element>
     *         &lt;element name="Authentication">
     *           &lt;complexType>
     *             &lt;complexContent>
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                 &lt;sequence>
     *                   &lt;element name="ResultIndicator" type="{http://www.w3.org/2001/XMLSchema}int"/>
     *                 &lt;/sequence>
     *                 &lt;attribute name="type" type="{http://www.w3.org/2001/XMLSchema}string" />
     *               &lt;/restriction>
     *             &lt;/complexContent>
     *           &lt;/complexType>
     *         &lt;/element>
     *         &lt;element name="Frontend" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *         &lt;element name="Processing">
     *           &lt;complexType>
     *             &lt;complexContent>
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                 &lt;sequence>
     *                   &lt;element name="Timestamp" type="{http://www.w3.org/2001/XMLSchema}dateTime"/>
     *                   &lt;element name="Result" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                   &lt;element name="Status">
     *                     &lt;complexType>
     *                       &lt;complexContent>
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                           &lt;attribute name="code" type="{http://www.w3.org/2001/XMLSchema}int" />
     *                         &lt;/restriction>
     *                       &lt;/complexContent>
     *                     &lt;/complexType>
     *                   &lt;/element>
     *                   &lt;element name="Reason">
     *                     &lt;complexType>
     *                       &lt;complexContent>
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                           &lt;attribute name="code" type="{http://www.w3.org/2001/XMLSchema}int" />
     *                         &lt;/restriction>
     *                       &lt;/complexContent>
     *                     &lt;/complexType>
     *                   &lt;/element>
     *                   &lt;element name="Return">
     *                     &lt;complexType>
     *                       &lt;complexContent>
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                           &lt;attribute name="code" type="{http://www.w3.org/2001/XMLSchema}string" />
     *                         &lt;/restriction>
     *                       &lt;/complexContent>
     *                     &lt;/complexType>
     *                   &lt;/element>
     *                   &lt;element name="Risk">
     *                     &lt;complexType>
     *                       &lt;complexContent>
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                           &lt;attribute name="score" type="{http://www.w3.org/2001/XMLSchema}int" />
     *                         &lt;/restriction>
     *                       &lt;/complexContent>
     *                     &lt;/complexType>
     *                   &lt;/element>
     *                   &lt;element name="ConnectorDetails" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                   &lt;element name="SecurityHash" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *                 &lt;/sequence>
     *                 &lt;attribute name="code" type="{http://www.w3.org/2001/XMLSchema}string" />
     *               &lt;/restriction>
     *             &lt;/complexContent>
     *           &lt;/complexType>
     *         &lt;/element>
     *         &lt;element name="Analysis">
     *           &lt;complexType>
     *             &lt;complexContent>
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                 &lt;sequence>
     *                   &lt;element name="Criterion" maxOccurs="unbounded">
     *                     &lt;complexType>
     *                       &lt;complexContent>
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *                           &lt;attribute name="name" type="{http://www.w3.org/2001/XMLSchema}string" />
     *                         &lt;/restriction>
     *                       &lt;/complexContent>
     *                     &lt;/complexType>
     *                   &lt;/element>
     *                 &lt;/sequence>
     *               &lt;/restriction>
     *             &lt;/complexContent>
     *           &lt;/complexType>
     *         &lt;/element>
     *       &lt;/sequence>
     *       &lt;attribute name="mode" type="{http://www.w3.org/2001/XMLSchema}string" />
     *       &lt;attribute name="channel" type="{http://www.w3.org/2001/XMLSchema}string" />
     *       &lt;attribute name="response" type="{http://www.w3.org/2001/XMLSchema}string" />
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "identification",
        "payment",
        "authentication",
        "frontend",
        "processing",
        "analysis"
    })
    public static class Transaction {

        @XmlElement(name = "Identification", required = true)
        protected AsyncPaymentResponse.Transaction.Identification identification;
        @XmlElement(name = "Payment", required = true)
        protected AsyncPaymentResponse.Transaction.Payment payment;
        @XmlElement(name = "Authentication", required = true)
        protected AsyncPaymentResponse.Transaction.Authentication authentication;
        @XmlElement(name = "Frontend", required = true)
        protected String frontend;
        @XmlElement(name = "Processing", required = true)
        protected AsyncPaymentResponse.Transaction.Processing processing;
        @XmlElement(name = "Analysis", required = true)
        protected AsyncPaymentResponse.Transaction.Analysis analysis;
        @XmlAttribute(name = "mode")
        protected String mode;
        @XmlAttribute(name = "channel")
        protected String channel;
        @XmlAttribute(name = "response")
        protected String response;

        public AsyncPaymentResponse.Transaction.Identification getIdentification() {
            return identification;
        }
        public void setIdentification(AsyncPaymentResponse.Transaction.Identification value) {
            this.identification = value;
        }

        public AsyncPaymentResponse.Transaction.Payment getPayment() {
            return payment;
        }

        public void setPayment(AsyncPaymentResponse.Transaction.Payment value) {
            this.payment = value;
        }

        public AsyncPaymentResponse.Transaction.Authentication getAuthentication() {
            return authentication;
        }

        public void setAuthentication(AsyncPaymentResponse.Transaction.Authentication value) {
            this.authentication = value;
        }

        public String getFrontend() {
            return frontend;
        }

        public void setFrontend(String value) {
            this.frontend = value;
        }

        public AsyncPaymentResponse.Transaction.Processing getProcessing() {
            return processing;
        }

        public void setProcessing(AsyncPaymentResponse.Transaction.Processing value) {
            this.processing = value;
        }

        public AsyncPaymentResponse.Transaction.Analysis getAnalysis() {
            return analysis;
        }
        public void setAnalysis(AsyncPaymentResponse.Transaction.Analysis value) {
            this.analysis = value;
        }

        public String getMode() {
            return mode;
        }
        public void setMode(String value) {
            this.mode = value;
        }

        public String getChannel() {
            return channel;
        }
        public void setChannel(String value) {
            this.channel = value;
        }

        public String getResponse() {
            return response;
        }
        public void setResponse(String value) {
            this.response = value;
        }

        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "", propOrder = {
            "criterion"
        })
        public static class Analysis {

            @XmlElement(name = "Criterion", required = true)
            protected List<AsyncPaymentResponse.Transaction.Analysis.Criterion> criterion;

            public List<AsyncPaymentResponse.Transaction.Analysis.Criterion> getCriterion() {
                if (criterion == null) {
                    criterion = new ArrayList<AsyncPaymentResponse.Transaction.Analysis.Criterion>();
                }
                return this.criterion;
            }

            @XmlAccessorType(XmlAccessType.FIELD)
            @XmlType(name = "")
            public static class Criterion {

                @XmlAttribute(name = "name")
                protected String name;

                public String getName() {
                    return name;
                }
                public void setName(String value) {
                    this.name = value;
                }

            }

        }


        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "", propOrder = {
            "resultIndicator"
        })
        public static class Authentication {

            @XmlElement(name = "ResultIndicator")
            protected int resultIndicator;
            @XmlAttribute(name = "type")
            protected String type;


            public int getResultIndicator() {
                return resultIndicator;
            }

            public void setResultIndicator(int value) {
                this.resultIndicator = value;
            }


            public String getType() {
                return type;
            }

            public void setType(String value) {
                this.type = value;
            }

        }

        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "", propOrder = {
            "shortID",
            "uniqueID"
        })
        public static class Identification {

            @XmlElement(name = "ShortID", required = true)
            protected String shortID;
            @XmlElement(name = "UniqueID", required = true)
            protected String uniqueID;


            public String getShortID() {
                return shortID;
            }


            public void setShortID(String value) {
                this.shortID = value;
            }

            public String getUniqueID() {
                return uniqueID;
            }

            public void setUniqueID(String value) {
                this.uniqueID = value;
            }

        }

        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "", propOrder = {
            "clearing"
        })
        public static class Payment {

            @XmlElement(name = "Clearing", required = true)
            protected AsyncPaymentResponse.Transaction.Payment.Clearing clearing;
            @XmlAttribute(name = "code")
            protected String code;

            public AsyncPaymentResponse.Transaction.Payment.Clearing getClearing() {
                return clearing;
            }


            public void setClearing(AsyncPaymentResponse.Transaction.Payment.Clearing value) {
                this.clearing = value;
            }

            public String getCode() {
                return code;
            }


            public void setCode(String value) {
                this.code = value;
            }

            @XmlAccessorType(XmlAccessType.FIELD)
            @XmlType(name = "", propOrder = {
                "amount",
                "currency",
                "descriptor",
                "fxRate",
                "fxSource",
                "fxDate"
            })
            public static class Clearing {

                @XmlElement(name = "Amount", required = true)
                protected BigDecimal amount;
                @XmlElement(name = "Currency", required = true)
                protected String currency;
                @XmlElement(name = "Descriptor", required = true)
                protected String descriptor;
                @XmlElement(name = "FxRate", required = true)
                protected BigDecimal fxRate;
                @XmlElement(name = "FxSource", required = true)
                protected String fxSource;
                @XmlElement(name = "FxDate", required = true)
                @XmlSchemaType(name = "dateTime")
                protected XMLGregorianCalendar fxDate;


                public BigDecimal getAmount() {
                    return amount;
                }


                public void setAmount(BigDecimal value) {
                    this.amount = value;
                }

                public String getCurrency() {
                    return currency;
                }


                public void setCurrency(String value) {
                    this.currency = value;
                }

                public String getDescriptor() {
                    return descriptor;
                }


                public void setDescriptor(String value) {
                    this.descriptor = value;
                }


                public BigDecimal getFxRate() {
                    return fxRate;
                }


                public void setFxRate(BigDecimal value) {
                    this.fxRate = value;
                }

                public String getFxSource() {
                    return fxSource;
                }


                public void setFxSource(String value) {
                    this.fxSource = value;
                }

                public XMLGregorianCalendar getFxDate() {
                    return fxDate;
                }

                public void setFxDate(XMLGregorianCalendar value) {
                    this.fxDate = value;
                }

            }

        }

        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "", propOrder = {
            "timestamp",
            "result",
            "status",
            "reason",
            "_return",
            "risk",
            "connectorDetails",
            "securityHash"
        })
        public static class Processing {

            @XmlElement(name = "Timestamp", required = true)
            @XmlSchemaType(name = "dateTime")
            protected XMLGregorianCalendar timestamp;
            @XmlElement(name = "Result", required = true)
            protected String result;
            @XmlElement(name = "Status", required = true)
            protected AsyncPaymentResponse.Transaction.Processing.Status status;
            @XmlElement(name = "Reason", required = true)
            protected AsyncPaymentResponse.Transaction.Processing.Reason reason;
            @XmlElement(name = "Return", required = true)
            protected AsyncPaymentResponse.Transaction.Processing.Return _return;
            @XmlElement(name = "Risk", required = true)
            protected AsyncPaymentResponse.Transaction.Processing.Risk risk;
            @XmlElement(name = "ConnectorDetails", required = true)
            protected String connectorDetails;
            @XmlElement(name = "SecurityHash", required = true)
            protected String securityHash;
            @XmlAttribute(name = "code")
            protected String code;

            public XMLGregorianCalendar getTimestamp() {
                return timestamp;
            }


            public void setTimestamp(XMLGregorianCalendar value) {
                this.timestamp = value;
            }


            public String getResult() {
                return result;
            }


            public void setResult(String value) {
                this.result = value;
            }

            public AsyncPaymentResponse.Transaction.Processing.Status getStatus() {
                return status;
            }


            public void setStatus(AsyncPaymentResponse.Transaction.Processing.Status value) {
                this.status = value;
            }


            public AsyncPaymentResponse.Transaction.Processing.Reason getReason() {
                return reason;
            }


            public void setReason(AsyncPaymentResponse.Transaction.Processing.Reason value) {
                this.reason = value;
            }


            public AsyncPaymentResponse.Transaction.Processing.Return getReturn() {
                return _return;
            }

            public void setReturn(AsyncPaymentResponse.Transaction.Processing.Return value) {
                this._return = value;
            }


            public AsyncPaymentResponse.Transaction.Processing.Risk getRisk() {
                return risk;
            }


            public void setRisk(AsyncPaymentResponse.Transaction.Processing.Risk value) {
                this.risk = value;
            }


            public String getConnectorDetails() {
                return connectorDetails;
            }


            public void setConnectorDetails(String value) {
                this.connectorDetails = value;
            }

            public String getSecurityHash() {
                return securityHash;
            }

            public void setSecurityHash(String value) {
                this.securityHash = value;
            }


            public String getCode() {
                return code;
            }
            public void setCode(String value) {
                this.code = value;
            }


            @XmlAccessorType(XmlAccessType.FIELD)
            @XmlType(name = "")
            public static class Reason {

                @XmlAttribute(name = "code")
                protected Integer code;


                public Integer getCode() {
                    return code;
                }
                public void setCode(Integer value) {
                    this.code = value;
                }

            }


            @XmlAccessorType(XmlAccessType.FIELD)
            @XmlType(name = "")
            public static class Return {

                @XmlAttribute(name = "code")
                protected String code;

                public String getCode() {
                    return code;
                }
                public void setCode(String value) {
                    this.code = value;
                }

            }



            @XmlAccessorType(XmlAccessType.FIELD)
            @XmlType(name = "")
            public static class Risk {

                @XmlAttribute(name = "score")
                protected Integer score;


                public Integer getScore() {
                    return score;
                }


                public void setScore(Integer value) {
                    this.score = value;
                }

            }

            @XmlAccessorType(XmlAccessType.FIELD)
            @XmlType(name = "")
            public static class Status {

                @XmlAttribute(name = "code")
                protected Integer code;

                public Integer getCode() {
                    return code;
                }
                public void setCode(Integer value) {
                    this.code = value;
                }

            }

        }

    }

}
