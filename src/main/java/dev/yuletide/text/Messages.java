package dev.yuletide.text;

import dev.yuletide.config.ConfigManager;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Nachrichten aus messages.json mit Prefix und Platzhaltern. */
public final class Messages {
	// Den Manager statt einzelner Texte halten: Nach einem Reload werden sofort die neuen Werte gelesen.
	private final ConfigManager configs;

	public Messages(ConfigManager configs) {
		this.configs = configs;
	}

	/** @param placeholders abwechselnd Name und Wert, z. B. {@code "player", name} */
	public Component get(String key, String... placeholders) {
		// Ein fehlender eigener Text faellt auf die englische Ressource zurueck.
		String template = configs.messages().str(key, configs.fallbackMessages().str(key, null));
		if (template == null) {
			return Text.legacy("&cMessage not found: " + key);
		}
		String prefix = configs.messages().str("prefix", configs.fallbackMessages().str("prefix", ""));
		// Der Prefix stammt aus der Konfiguration und darf selbst Farbcodes enthalten.
		// Normale Platzhalter werden dagegen erst von Text.legacy als Klartext eingesetzt.
		template = template.replace("{prefix}", prefix);
		return Text.legacy(template, map(placeholders));
	}

	/** Wie {@link #get}, aber {@code {markupName}} wird als Legacy-Text mit Farbcodes eingesetzt (nur für vertrauenswürdige Konfigurationswerte). */
	public Component getWithMarkup(String key, String markupName, String markup, String... placeholders) {
		String template = configs.messages().str(key, configs.fallbackMessages().str(key, null));
		if (template == null) {
			return Text.legacy("&cMessage not found: " + key);
		}
		String prefix = configs.messages().str("prefix", configs.fallbackMessages().str("prefix", ""));
		// Bewusste Ausnahme: z. B. einen farbigen Cracker-Namen VOR dem Farbparser einsetzen.
		template = template.replace("{prefix}", prefix).replace("{" + markupName + "}", markup);
		return Text.legacy(template, map(placeholders));
	}

	/** Macht aus Name/Wert-Paaren eine Map; ein einzelnes letztes Element ohne Wert wird ignoriert. */
	public static Map<String, String> map(String... placeholders) {
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i + 1 < placeholders.length; i += 2) {
			map.put(placeholders[i], placeholders[i + 1]);
		}
		return map;
	}
}
