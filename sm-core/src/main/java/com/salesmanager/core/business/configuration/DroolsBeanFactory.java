package com.salesmanager.core.business.configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;

import org.drools.decisiontable.DecisionTableProviderImpl;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.DecisionTableConfiguration;
import org.kie.internal.builder.DecisionTableInputType;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Compiles each DRL rule file once and keeps the resulting {@link KieContainer}
 * for the lifetime of the application. Callers get a new lightweight
 * {@link KieSession} per evaluation and must {@link KieSession#dispose()} it.
 */
@Component
public class DroolsBeanFactory {

	private static final Logger LOGGER = LoggerFactory.getLogger(DroolsBeanFactory.class);

	public static final String RULES_PATH = "com/salesmanager/drools/rules/";

	private static final String RELEASE_GROUP = "com.salesmanager.drools";
	private static final String RELEASE_VERSION = "1.0.0";

	private final KieServices kieServices = KieServices.Factory.get();

	private final Map<String, KieContainer> containers = new ConcurrentHashMap<>();

	/**
	 * @param ruleFile DRL file name under {@link #RULES_PATH}, e.g. PromoCoupon.drl
	 */
	public KieSession newKieSession(String ruleFile) {
		return getKieContainer(ruleFile).newKieSession();
	}

	public KieContainer getKieContainer(String ruleFile) {
		return containers.computeIfAbsent(ruleFile, this::buildContainer);
	}

	private KieContainer buildContainer(String ruleFile) {
		String classPath = RULES_PATH + ruleFile;
		ReleaseId releaseId = kieServices.newReleaseId(RELEASE_GROUP,
				"rules-" + ruleFile.replaceAll("[^A-Za-z0-9]", "-"), RELEASE_VERSION);

		KieFileSystem kieFileSystem = kieServices.newKieFileSystem()
				.generateAndWritePomXML(releaseId)
				.write(ResourceFactory.newClassPathResource(classPath));

		KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem).buildAll();
		if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
			throw new IllegalStateException(
					"Unable to compile rules " + classPath + ": " + kieBuilder.getResults().getMessages());
		}

		LOGGER.info("Compiled rules {}", classPath);
		return kieServices.newKieContainer(releaseId);
	}

	@PreDestroy
	public void dispose() {
		containers.values().forEach(KieContainer::dispose);
		containers.clear();
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
