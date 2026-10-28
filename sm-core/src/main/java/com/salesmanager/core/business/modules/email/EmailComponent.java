package com.salesmanager.core.business.modules.email;

import javax.inject.Inject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("htmlEmailSender")
public class EmailComponent implements HtmlEmailSender {
  
  @Value("${config.emailSender}")
  private String emailSender;

  @Inject
  private EmailModule defaultEmailSender;

  @Inject
  private EmailModule sesEmailSender;

  @Override
  public void send(Email email) throws Exception {
    send(email, null);
  }

  @Override
  public void send(Email email, EmailConfig emailConfig) throws Exception {
    switch(emailSender) 
    { 
        case "default": 
          defaultEmailSender.send(email, emailConfig);
            break; 
        case "ses": 
          sesEmailSender.send(email, emailConfig);
            break; 
        default: 
            throw new Exception("No email implementation for " + emailSender); 
    }
    
  }

}
