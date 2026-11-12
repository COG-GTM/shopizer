package com.salesmanager.core.business.configuration;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.drools.compiler.kie.builder.impl.DrlProject;
import org.drools.decisiontable.DecisionTableProviderImpl;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.DecisionTableConfiguration;
import org.kie.internal.builder.DecisionTableInputType;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DroolsBeanFactory {
	
	
	@Value("${config.shipping.rule.priceByDistance}")
	private String priceByDistance;
	
	@Value("${config.shipping.rule.shippingModuleDecision}")
	private String shippingDecision;
	

    private static final String RULES_PATH = "com/salesmanager/drools/rules/";
    private KieServices kieServices = KieServices.Factory.get();

    private  KieFileSystem getKieFileSystem() throws IOException{
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        List<String> rules=Arrays.asList(priceByDistance,shippingDecision);
        for(String rule:rules){
            kieFileSystem.write(ResourceFactory.newClassPathResource(rule));
        }
        return kieFileSystem;
    }

    public KieContainer getKieContainer() throws IOException {
        KieBuilder kb = kieServices.newKieBuilder(getKieFileSystem());
        buildRules(kb);

        KieModule kieModule = kb.getKieModule();

        return kieServices.newKieContainer(kieModule.getReleaseId());

    }

    private void buildRules(KieBuilder builder) {
        builder.buildAll(DrlProject.class);
        if (builder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("Unable to build Drools rules: " + builder.getResults());
        }
    }

    public KieSession getKieSession(){
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        kieFileSystem.write(ResourceFactory.newClassPathResource(RULES_PATH + priceByDistance));
        kieFileSystem.write(ResourceFactory.newClassPathResource(RULES_PATH + shippingDecision));
        
        KieBuilder kb = kieServices.newKieBuilder(kieFileSystem);
        buildRules(kb);
        KieModule kieModule = kb.getKieModule();

        KieContainer kContainer = kieServices.newKieContainer(kieModule.getReleaseId());

        return kContainer.newKieSession();

    }

    public KieSession getKieSession(Resource dt) {
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem()
            .write(dt);

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        buildRules(kieBuilder);
        KieContainer kieContainer = kieServices.newKieContainer(kieBuilder.getKieModule().getReleaseId());

        return kieContainer.newKieSession();
    }

    /*
     * Can be used for debugging
     * Input excelFile example: com/baeldung/drools/rules/Discount.xls
     */
    public String getDrlFromExcel(String excelFile) {
        DecisionTableConfiguration configuration = KnowledgeBuilderFactory.newDecisionTableConfiguration();
        configuration.setInputType(DecisionTableInputType.XLS);

        Resource dt = ResourceFactory.newClassPathResource(excelFile, getClass());

        DecisionTableProviderImpl decisionTableProvider = new DecisionTableProviderImpl();

        return decisionTableProvider.loadFromResource(dt, null);
    }

}
