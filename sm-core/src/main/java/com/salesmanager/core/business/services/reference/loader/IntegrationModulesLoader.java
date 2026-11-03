package com.salesmanager.core.business.services.reference.loader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.model.system.ModuleConfig;

@Component
public class IntegrationModulesLoader {
	

	private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationModulesLoader.class);

	private static final String REFERENCE_MODULES = "reference/integrationmodules.json";

	private static final String NOT_REQUIRED = "NOTREQUIRED";

	private volatile Map<String, Set<String>> allowedEndpoints;
	

	public List<IntegrationModule> loadIntegrationModules(String jsonFilePath) throws Exception {
		
		
		List<IntegrationModule> modules = new ArrayList<IntegrationModule>();
		
		ObjectMapper mapper = new ObjectMapper();
		
		try {
			
            InputStream in =
                this.getClass().getClassLoader().getResourceAsStream(jsonFilePath);
			
            
            @SuppressWarnings("rawtypes")
			Map[] objects = mapper.readValue(in, Map[].class);

			for (Map object : objects) {

				modules.add(this.loadModule(object));
			}
            
            return modules;

  		} catch (Exception e) {
  			throw new ServiceException(e);
  		}
  		
  		

		
	
	
	
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public IntegrationModule loadModule(Map object) throws Exception {
		
			ObjectMapper mapper = new ObjectMapper();
	    	IntegrationModule module = new IntegrationModule();
	    	module.setModule((String)object.get("module"));
	    	module.setCode((String)object.get("code"));
	    	module.setImage((String)object.get("image"));
	    	
	    	if(object.get("type")!=null) {
	    		module.setType((String)object.get("type"));
	    	}
	    	
	    	if(object.get("customModule")!=null) {
	    		Object o = object.get("customModule");
	    		Boolean b = false;
	    		if(o instanceof Boolean) {
	    			b = (Boolean)object.get("customModule");
	    		} else {
	    			try {
	    				b = Boolean.valueOf((String) object.get("customModule"));
	    			} catch(Exception e) {
	    				LOGGER.error("Cannot cast " + o.getClass() + " tp a boolean value");
	    			}
	    		}
	    		module.setCustomModule(b);
	    	}
	    	//module.setRegions(regions)
	    	if(object.get("details")!=null) {
	    		
	    		Map<String,String> details = (Map<String,String>)object.get("details");
	    		module.setDetails(details);
	    		
	    		//maintain the original json structure
	    		StringBuilder detailsStructure = new StringBuilder();
	    		int count = 0;
	    		detailsStructure.append("{");
	    		for(String key : details.keySet()) {
	    			String jsonKeyString = mapper.writeValueAsString(key);
	    			detailsStructure.append(jsonKeyString);
	    			detailsStructure.append(":");
	    			String jsonValueString = mapper.writeValueAsString(details.get(key));
	    			detailsStructure.append(jsonValueString);
	        		if(count<(details.size()-1)) {
	        			detailsStructure.append(",");
	        		}
	        		count++;
	    		}
	    		detailsStructure.append("}");
	    		module.setConfigDetails(detailsStructure.toString());
	    		
	    	}
	    	
	    	
	    	List confs = (List)object.get("configuration");
	    	
	    	//convert to json
	    	
	    	
	    	
	    	if(confs!=null) {
	    		StringBuilder configString = new StringBuilder();
	    		configString.append("[");
	    		Map<String,ModuleConfig> moduleConfigs = new HashMap<String,ModuleConfig>();
	        	int count=0;
	    		for(Object oo : confs) {
	        		
	        		Map values = (Map)oo;
	        		
	        		String env = (String)values.get("env");
	        		
	        		ModuleConfig config = new ModuleConfig();
	        		config.setScheme((String)values.get("scheme"));
	        		config.setHost((String)values.get("host"));
	        		config.setPort((String)values.get("port"));
	        		config.setUri((String)values.get("uri"));
	        		config.setEnv((String)values.get("env"));
	        		validateEndpoint(module.getCode(), config);
	        		if(values.get("config1") !=null) {
	        			config.setConfig1((String)values.get("config1"));
	        		}
	        		if(values.get("config2") !=null) {
	        			config.setConfig2((String)values.get("config2"));
	        		}
	        		
	        		String jsonConfigString = mapper.writeValueAsString(config);
	        		configString.append(jsonConfigString);
	        		
	        		moduleConfigs.put(env, config);
	        		
	        		if(count<(confs.size()-1)) {
	        			configString.append(",");
	        		}
	        		count++;
	        		
	        		
	        	}
	        	configString.append("]");
	        	module.setConfiguration(configString.toString());
	        	module.setModuleConfigs(moduleConfigs);
	    	}
	    	
	    	List<String> regions = (List<String>)object.get("regions");
	    	if(regions!=null) {
	    		
	
	    		StringBuilder configString = new StringBuilder();
	    		configString.append("[");
	    		int count=0;
	    		for(String region : regions) {
	    			
	    			module.getRegionsSet().add(region);
	    			String jsonConfigString = mapper.writeValueAsString(region);
	    			configString.append(jsonConfigString);
	    			
	        		if(count<(regions.size()-1)) {
	        			configString.append(",");
	        		}
	        		count++;
	
	    		}
	    		configString.append("]");
	    		module.setRegions(configString.toString());
	
	    	}
	    	
	    	return module;
    	
		
	}

	/**
	 * Only the scheme/host pairs declared for a module in the bundled
	 * reference/integrationmodules.json are accepted, so a module definition
	 * cannot redirect gateway traffic to an arbitrary server.
	 */
	private void validateEndpoint(String code, ModuleConfig config) throws Exception {
		String host = StringUtils.trimToEmpty(config.getHost());
		if (host.isEmpty() || NOT_REQUIRED.equalsIgnoreCase(host)) {
			return;
		}

		Set<String> allowed = getAllowedEndpoints().get(code);
		if (allowed == null || !allowed.contains(endpointKey(config.getScheme(), host))) {
			throw new ServiceException("Endpoint " + config.getScheme() + "://" + host
					+ " is not allowed for integration module " + code);
		}

		String port = StringUtils.trimToEmpty(config.getPort());
		if (!port.isEmpty() && !NOT_REQUIRED.equalsIgnoreCase(port) && !port.matches("\\d{1,5}")) {
			throw new ServiceException("Invalid port " + port + " for integration module " + code);
		}

		String uri = StringUtils.trimToEmpty(config.getUri());
		if (!uri.isEmpty() && !NOT_REQUIRED.equalsIgnoreCase(uri) && (!uri.startsWith("/") || uri.startsWith("//"))) {
			throw new ServiceException("Invalid uri " + uri + " for integration module " + code);
		}
	}

	@SuppressWarnings("rawtypes")
	private Map<String, Set<String>> getAllowedEndpoints() throws Exception {
		Map<String, Set<String>> endpoints = allowedEndpoints;
		if (endpoints != null) {
			return endpoints;
		}
		synchronized (this) {
			if (allowedEndpoints == null) {
				Map<String, Set<String>> loaded = new HashMap<String, Set<String>>();
				ObjectMapper mapper = new ObjectMapper();
				try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(REFERENCE_MODULES)) {
					if (in == null) {
						throw new ServiceException("Cannot load " + REFERENCE_MODULES);
					}
					Map[] objects = mapper.readValue(in, Map[].class);
					for (Map object : objects) {
						List confs = (List) object.get("configuration");
						if (confs == null) {
							continue;
						}
						Set<String> keys = new HashSet<String>();
						for (Object conf : confs) {
							Map values = (Map) conf;
							String host = StringUtils.trimToEmpty((String) values.get("host"));
							if (!host.isEmpty() && !NOT_REQUIRED.equalsIgnoreCase(host)) {
								keys.add(endpointKey((String) values.get("scheme"), host));
							}
						}
						loaded.put((String) object.get("code"), keys);
					}
				}
				allowedEndpoints = loaded;
			}
			return allowedEndpoints;
		}
	}

	private static String endpointKey(String scheme, String host) {
		return StringUtils.trimToEmpty(scheme).toLowerCase(Locale.ROOT) + "://"
				+ StringUtils.trimToEmpty(host).toLowerCase(Locale.ROOT);
	}

}
