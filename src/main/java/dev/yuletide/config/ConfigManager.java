package dev.yuletide.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.yuletide.Yuletide;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Lädt config.json, gifts.json und messages.json aus {@code config/yuletide/}; fehlende Dateien werden aus den Standardwerten erzeugt. */
public final class ConfigManager {
	// Fabric liefert den passenden Konfigurationsordner fuer Client oder dedizierten Server.
	private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("yuletide");

	private Cfg config = Cfg.EMPTY;
	private Cfg gifts = Cfg.EMPTY;
	private Cfg messages = Cfg.EMPTY;
	private Cfg fallbackMessages = Cfg.EMPTY;

	public Path dir() {
		return dir;
	}

	public Cfg config() {
		return config;
	}

	public Cfg gifts() {
		return gifts;
	}

	public Cfg messages() {
		return messages;
	}

	public Cfg fallbackMessages() {
		return fallbackMessages;
	}

	/**
	 * Liest alle Einstellungen neu ein. Bereits vorhandene Dateien bleiben unveraendert,
	 * damit eigene Einstellungen und Uebersetzungen beim Reload erhalten bleiben.
	 */
	public void load() {
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			Yuletide.LOGGER.error("Could not create config directory {}", dir, e);
		}
		config = read("config.json", "config.json");
		// Die Sprache wird zuerst aus config.json bestimmt, danach die Nachrichten geladen.
		String language = config.str("general.language", "en").toLowerCase();
		if (!language.equals("de")) {
			language = "en";
		}
		gifts = read("gifts.json", "gifts.json");
		// Die Sprachwahl bestimmt die Vorlage fuer eine NEUE messages.json.
		// Eine vorhandene messages.json wird durch einen Sprachwechsel nicht ersetzt.
		messages = read("messages.json", "messages_" + language + ".json");
		// Einzelne fehlende Nachrichtenschluessel koennen spaeter auf Englisch zurueckfallen.
		fallbackMessages = new Cfg(readResource("messages_en.json"));
	}

	/** Liest die Datei; bei fehlender Datei wird sie aus der Ressource erzeugt, bei Syntaxfehlern gelten die Standardwerte. */
	private Cfg read(String fileName, String resourceName) {
		Path file = dir.resolve(fileName);
		if (!Files.exists(file)) {
			try (InputStream in = open(resourceName)) {
				Files.copy(in, file);
			} catch (IOException e) {
				Yuletide.LOGGER.error("Could not write default {}", fileName, e);
			}
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (parsed.isJsonObject()) {
				return new Cfg(parsed.getAsJsonObject());
			}
			Yuletide.LOGGER.error("{} must contain a JSON object - using defaults", file);
		} catch (JsonParseException | IOException e) {
			Yuletide.LOGGER.error("Could not read {} ({}) - using built-in defaults", file, e.getMessage());
		}
		// Fehlerhafte Benutzerdateien nicht ueberschreiben; Standardwerte nur im Speicher verwenden.
		return new Cfg(readResource(resourceName));
	}

	private static JsonObject readResource(String name) {
		try (InputStream in = open(name); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Missing built-in resource " + name, e);
		}
	}

	/** Liest eine mitgelieferte Ressource aus dem Klassenpfad, im fertigen Build also aus der Mod-JAR. */
	private static InputStream open(String name) throws IOException {
		InputStream in = ConfigManager.class.getResourceAsStream("/yuletide/" + name);
		if (in == null) {
			throw new IOException("Missing built-in resource " + name);
		}
		return in;
	}
}
