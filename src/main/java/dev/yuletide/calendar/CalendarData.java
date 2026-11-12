package dev.yuletide.calendar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.yuletide.Yuletide;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Abgeholte Tuerchen je Jahr und Spieler in {@code <welt>/data/yuletide_calendar.json}.
 * Speichert nur Fortschritt; Datumsregeln und Geschenkvergabe gehoeren zum CalendarManager.
 */
public final class CalendarData {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// Verschachtelte Struktur: Jahr -> Spieler-UUID -> Menge abgeholter Tage.
	// UUIDs bleiben bei Namensaenderungen gleich; das Set verhindert doppelte Tage.
	private final Map<Integer, Map<UUID, Set<Integer>>> claimed = new HashMap<>();
	private Path file;

	/** Ersetzt den Speicherinhalt durch die Datei; fehlende oder unlesbare Daten ergeben einen leeren Stand. */
	public void load(Path file) {
		this.file = file;
		claimed.clear();
		if (!Files.exists(file)) {
			return;
		}
		// try-with-resources schliesst den Reader automatisch, auch bei einer Exception.
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (Map.Entry<String, JsonElement> year : root.entrySet()) {
				Map<UUID, Set<Integer>> players = new HashMap<>();
				for (Map.Entry<String, JsonElement> player : year.getValue().getAsJsonObject().entrySet()) {
					Set<Integer> days = new HashSet<>();
					for (JsonElement day : player.getValue().getAsJsonArray()) {
						days.add(day.getAsInt());
					}
					// JSON-Objektschluessel sind Strings; im Java-Modell nutzen wir echte UUIDs und Jahreszahlen.
					players.put(UUID.fromString(player.getKey()), days);
				}
				claimed.put(Integer.parseInt(year.getKey()), players);
			}
		} catch (IOException | RuntimeException e) {
			Yuletide.LOGGER.error("Could not read {} - calendar progress starts empty: {}", file, e.getMessage());
			claimed.clear();
		}
	}

	public boolean isClaimed(int year, UUID player, int day) {
		return claimed.getOrDefault(year, Map.of()).getOrDefault(player, Set.of()).contains(day);
	}

	public Set<Integer> claimedDays(int year, UUID player) {
		// Eine unveraenderliche Kopie verhindert Aenderungen am internen Speicher von aussen.
		return Set.copyOf(claimed.getOrDefault(year, Map.of()).getOrDefault(player, Set.of()));
	}

	public void claim(int year, UUID player, int day) {
		// computeIfAbsent legt die jeweilige Unterstruktur nur an, wenn sie noch fehlt.
		claimed.computeIfAbsent(year, y -> new HashMap<>()).computeIfAbsent(player, p -> new HashSet<>()).add(day);
		save();
	}

	public void reset(int year, UUID player, int day) {
		Set<Integer> days = claimed.getOrDefault(year, Map.of()).get(player);
		if (days != null) {
			days.remove(day);
			save();
		}
	}

	/** Entfernt den Spieler auch aus der Datei (Fehler 7 des Originals). */
	public void resetAll(int year, UUID player) {
		Map<UUID, Set<Integer>> players = claimed.get(year);
		if (players != null && players.remove(player) != null) {
			save();
		}
	}

	/** Schreibt den aktuellen Stand; vor dem ersten load-Aufruf ist noch kein Speicherpfad bekannt. */
	public void save() {
		if (file == null) {
			return;
		}
		JsonObject root = new JsonObject();
		for (Map.Entry<Integer, Map<UUID, Set<Integer>>> year : claimed.entrySet()) {
			JsonObject players = new JsonObject();
			for (Map.Entry<UUID, Set<Integer>> player : year.getValue().entrySet()) {
				if (player.getValue().isEmpty()) {
					continue;
				}
				JsonArray days = new JsonArray();
				// Sortierte Tage machen die JSON-Datei fuer Menschen leichter lesbar.
				new TreeSet<>(player.getValue()).forEach(days::add);
				players.add(player.getKey().toString(), days);
			}
			root.add(String.valueOf(year.getKey()), players);
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			// Erst die vollstaendige temporaere Datei atomar austauschen. So wird die
			// bisherige Datei nicht schon waehrend des Schreibens ueberschrieben.
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Yuletide.LOGGER.error("Could not save {}", file, e);
		}
	}
}
