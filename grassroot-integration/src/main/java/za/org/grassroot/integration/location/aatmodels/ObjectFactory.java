
package za.org.grassroot.integration.location.aatmodels;

import javax.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the za.org.grassroot.integration.location.aatmodels package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: za.org.grassroot.integration.location.aatmodels
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link GetLocationEntity }
     * 
     */
    public GetLocationEntity createGetLocation() {
        return new GetLocationEntity();
    }

    /**
     * Create an instance of {@link GetLocationEntity.Result }
     * 
     */
    public GetLocationEntity.Result createGetLocationResult() {
        return new GetLocationEntity.Result();
    }

}
