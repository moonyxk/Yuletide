package dev.yuletide.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class CfgTest {
	@Test
	void readsNestedValuesAndUsesDefaultsForMissingOrInvalidValues() {
		Cfg cfg = new Cfg(JsonParser.parseString("""
			{
			  "calendar": {
			    "enabled": false,
			    "first-day": 24.9,
			    "timezone": "UTC",
			    "invalid-number": "not-a-number",
			    "null-value": null
			  }
			}
			""").getAsJsonObject());

		assertFalse(cfg.bool("calendar.enabled", true));
		assertEquals(24, cfg.integer("calendar.first-day", 1));
		assertEquals("UTC", cfg.str("calendar.timezone", ""));
		assertEquals(1.5, cfg.dbl("calendar.invalid-number", 1.5));
		assertTrue(cfg.bool("calendar.missing", true));
		assertEquals("fallback", cfg.str("calendar.null-value", "fallback"));
		assertFalse(cfg.has("calendar.null-value"));
		assertNull(cfg.section("calendar.enabled"));
	}

	@Test
	void filtersNonMatchingEntriesFromArrays() {
		Cfg cfg = new Cfg(JsonParser.parseString("""
			{
			  "group": {
			    "tags": ["vip", 3, null, {"ignored": true}],
			    "items": [{"name": "first"}, 3, null, {"name": "second"}]
			  }
			}
			""").getAsJsonObject());
		Cfg group = cfg.section("group");

		assertNotNull(group);
		assertEquals(List.of("vip", "3"), group.strings("tags"));
		assertEquals(List.of("first", "second"),
			group.list("items").stream().map(item -> item.str("name", "")).toList());
	}
}
