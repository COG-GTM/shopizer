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
@XmlRootElement(name="price-quotes")
public class PriceQuotes {
    @XmlElement(name="price-quote")
    private List<PriceQuote> priceQuotes = new ArrayList<>();
    public List<PriceQuote> getPriceQuotes() { return priceQuotes; }
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PriceQuote {
        @XmlElement(name="service-code")
        @XmlJavaTypeAdapter(NormalizedStringAdapter.class)
        private String serviceCode;
        public String getServiceCode() { return serviceCode; }
        public void setServiceCode(String value) { serviceCode = value; }
        @XmlElement(name="service-name")
        @XmlJavaTypeAdapter(NormalizedStringAdapter.class)
        private String serviceName;
        public String getServiceName() { return serviceName; }
        public void setServiceName(String value) { serviceName = value; }
        @XmlElement(name="price-details")
        private PriceDetails priceDetails;
        public PriceDetails getPriceDetails() { return priceDetails; }
        public void setPriceDetails(PriceDetails value) { priceDetails = value; }
        @XmlAccessorType(XmlAccessType.FIELD)
        public static class PriceDetails {
            private BigDecimal base;
            public BigDecimal getBase() { return base; }
            public void setBase(BigDecimal value) { base = value; }
            private BigDecimal due;
            public BigDecimal getDue() { return due; }
            public void setDue(BigDecimal value) { due = value; }
        }
    }
}
