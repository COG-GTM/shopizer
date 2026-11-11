package ca.canadapost.cpcdp.rating.generated.messages;

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
@XmlRootElement(name="messages")
public class Messages {
    @XmlElement(name="message")
    private List<Message> message = new ArrayList<>();
    public List<Message> getMessage() { return message; }
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Message {
        @XmlJavaTypeAdapter(NormalizedStringAdapter.class)
        private String code;
        public String getCode() { return code; }
        public void setCode(String value) { code = value; }
        @XmlJavaTypeAdapter(NormalizedStringAdapter.class)
        private String description;
        public String getDescription() { return description; }
        public void setDescription(String value) { description = value; }
    }
}
