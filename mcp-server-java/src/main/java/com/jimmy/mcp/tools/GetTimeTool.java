package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Returns the current time. Demonstrates a tool that reads real state (system clock).
 * Optional "zone" argument, defaults to Asia/Taipei.
 */
@Component
public class GetTimeTool implements McpTool {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "get_time";
    }

    @Override
    public String getDescription() {
        return "Returns the current date and time. Optional `zone` argument (IANA timezone, e.g. 'Asia/Taipei', 'UTC').";
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return mapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "zone": {
                      "type": "string",
                      "description": "IANA timezone (e.g., 'Asia/Taipei', 'UTC'). Defaults to Asia/Taipei."
                    }
                  }
                }
                """);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build GetTimeTool schema", e);
        }
    }

    @Override
    public String execute(JsonNode args) {
        String zoneName = "Asia/Taipei";
        if (args != null && args.has("zone") && !args.get("zone").isNull()) {
            zoneName = args.get("zone").asText();
        }
        try {
            ZoneId zone = ZoneId.of(zoneName);
            ZonedDateTime now = ZonedDateTime.now(zone);
            return now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + " (" + zone.getId() + ")";
        } catch (Exception e) {
            return "Invalid timezone: " + zoneName + ". Use IANA format like 'Asia/Taipei' or 'UTC'.";
        }
    }
}
