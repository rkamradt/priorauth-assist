package net.kamradtfamily.priorauth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    public void log(String toolName, Map<String, Object> args, String callerContext) {
        log.info("TOOL_CALL | tool={} | patientId={} | service={} | caller={}",
            toolName,
            args.getOrDefault("patient_id", args.getOrDefault("patientId", "-")),
            args.getOrDefault("service", "-"),
            callerContext);
    }
}
