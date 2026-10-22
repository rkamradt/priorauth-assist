package net.kamradtfamily.priorauth.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class RuleLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private Map<String, CoverageRule> ruleCache;

    @PostConstruct
    public void init() {
        try {
            ruleCache = loadAll();
            log.info("Loaded {} coverage rules", ruleCache.size());
        } catch (IOException e) {
            log.error("Failed to load coverage rules", e);
            ruleCache = Map.of();
        }
    }

    public Map<String, CoverageRule> loadAll() throws IOException {
        Map<String, CoverageRule> rules = new LinkedHashMap<>();
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/rules/*.yaml");
        for (Resource resource : resources) {
            try {
                CoverageRule rule = yamlMapper.readValue(resource.getInputStream(), CoverageRule.class);
                rules.put(rule.getId(), rule);
                log.debug("Loaded rule: {} ({})", rule.getId(), rule.getService());
            } catch (IOException e) {
                log.warn("Failed to parse rule file {}: {}", resource.getFilename(), e.getMessage());
            }
        }
        return rules;
    }

    public CoverageRule findByService(String query) {
        if (query == null || ruleCache == null) {
            return null;
        }
        String lowerQuery = query.toLowerCase();
        return ruleCache.values().stream()
            .filter(rule -> rule.getService() != null &&
                rule.getService().toLowerCase().contains(lowerQuery))
            .findFirst()
            .orElse(null);
    }

    public Map<String, CoverageRule> getRuleCache() {
        return ruleCache != null ? ruleCache : Map.of();
    }
}
