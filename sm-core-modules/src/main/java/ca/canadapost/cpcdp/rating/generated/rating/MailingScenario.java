package ca.canadapost.cpcdp.rating.generated.rating;

import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.adapters.NormalizedStringAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="mailing-scenario")
public class MailingScenario {
    @XmlElement(name="customer-number")
    private String customerNumber;
    public String getCustomerNumber() { return customerNumber; }
    public void setCustomerNumber(String value) { customerNumber = value; }
    @XmlElement(name="parcel-characteristics")
    private ParcelCharacteristics parcelCharacteristics;
    public ParcelCharacteristics getParcelCharacteristics() { return parcelCharacteristics; }
    public void setParcelCharacteristics(ParcelCharacteristics value) { parcelCharacteristics = value; }
    private Services services;
    public Services getServices() { return services; }
    public void setServices(Services value) { services = value; }
    @XmlElement(name="origin-postal-code")
    private String originPostalCode;
    public String getOriginPostalCode() { return originPostalCode; }
    public void setOriginPostalCode(String value) { originPostalCode = value; }
    private Destination destination;
    public Destination getDestination() { return destination; }
    public void setDestination(Destination value) { destination = value; }
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Services {
        @XmlElement(name="service-code")
        @XmlJavaTypeAdapter(NormalizedStringAdapter.class)
        private List<String> serviceCodes = new ArrayList<>();
        public List<String> getServiceCodes() { return serviceCodes; }
    }
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ParcelCharacteristics {
        private BigDecimal weight;
        public BigDecimal getWeight() { return weight; }
        public void setWeight(BigDecimal value) { weight = value; }
        private Dimensions dimensions;
        public Dimensions getDimensions() { return dimensions; }
        public void setDimensions(Dimensions value) { dimensions = value; }
        @XmlAccessorType(XmlAccessType.FIELD)
        public static class Dimensions {
            private BigDecimal length;
            public BigDecimal getLength() { return length; }
            public void setLength(BigDecimal value) { length = value; }
            private BigDecimal width;
            public BigDecimal getWidth() { return width; }
            public void setWidth(BigDecimal value) { width = value; }
            private BigDecimal height;
            public BigDecimal getHeight() { return height; }
            public void setHeight(BigDecimal value) { height = value; }
        }
    }
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Destination {
        private Domestic domestic;
        public Domestic getDomestic() { return domestic; }
        public void setDomestic(Domestic value) { domestic = value; }
        @XmlElement(name="united-states")
        private UnitedStates unitedStates;
        public UnitedStates getUnitedStates() { return unitedStates; }
        public void setUnitedStates(UnitedStates value) { unitedStates = value; }
        private International international;
        public International getInternational() { return international; }
        public void setInternational(International value) { international = value; }
        @XmlAccessorType(XmlAccessType.FIELD)
        public static class Domestic {
            @XmlElement(name="postal-code")
            private String postalCode;
            public String getPostalCode() { return postalCode; }
            public void setPostalCode(String value) { postalCode = value; }
        }
        @XmlAccessorType(XmlAccessType.FIELD)
        public static class UnitedStates {
            @XmlElement(name="zip-code")
            private String zipCode;
            public String getZipCode() { return zipCode; }
            public void setZipCode(String value) { zipCode = value; }
        }
        @XmlAccessorType(XmlAccessType.FIELD)
        public static class International {
            @XmlElement(name="country-code")
            private String countryCode;
            public String getCountryCode() { return countryCode; }
            public void setCountryCode(String value) { countryCode = value; }
        }
    }
}
