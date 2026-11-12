package dev.yuletide.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Lesezugriff auf ein JsonObject ueber Punkt-Pfade, z. B. {@code santa-claus.gift-drop.chance}.
 * Buendelt JSON-Zugriffe und Standardwerte, damit Spiellogik nicht selbst durch JSON-Baeume navigieren muss.
 */
public final class Cfg {
	public static final Cfg EMPTY = new Cfg(new JsonObject());

	private final JsonObject root;

	public Cfg(JsonObject root) {
		this.root = root;
	}

	public JsonObject raw() {
		return root;
	}

	/**
	 * Steigt pro Pfadteil eine Objektebene tiefer. {@code @Nullable} kennzeichnet,
	 * dass fehlende Werte und unpassende Zwischenknoten als null zurueckkommen koennen.
	 */
	public @Nullable JsonElement find(String path) {
		JsonElement current = root;
		// split erwartet einen regulaeren Ausdruck: "\\." meint einen echten Punkt.
		for (String part : path.split("\\.")) {
			if (current == null || !current.isJsonObject()) {
				return null;
			}
			current = current.getAsJsonObject().get(part);
		}
		return current == null || current.isJsonNull() ? null : current;
	}

	public boolean has(String path) {
		return find(path) != null;
	}

	public String str(String path, String def) {
		JsonElement e = find(path);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
	}

	public int integer(String path, int def) {
		// Der Cast schneidet Nachkommastellen ab, statt die Zahl zu runden.
		return (int) dbl(path, def);
	}

	/** Verwendet def bei fehlenden Werten oder einer nicht lesbaren Zahl. */
	public double dbl(String path, double def) {
		JsonElement e = find(path);
		if (e != null && e.isJsonPrimitive()) {
			try {
				return e.getAsDouble();
			} catch (NumberFormatException ex) {
				return def;
			}
		}
		return def;
	}

	public boolean bool(String path, boolean def) {
		JsonElement e = find(path);
		return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
	}

	public List<String> strings(String path) {
		List<String> out = new ArrayList<>();
		JsonElement e = find(path);
		if (e != null && e.isJsonArray()) {
			for (JsonElement item : e.getAsJsonArray()) {
				if (item.isJsonPrimitive()) {
					out.add(item.getAsString());
				}
			}
		}
		return out;
	}

	/** Verpackt ein Unterobjekt in denselben Helfer; es ist keine Kopie des JSON-Baums. */
	public @Nullable Cfg section(String path) {
		JsonElement e = find(path);
		return e != null && e.isJsonObject() ? new Cfg(e.getAsJsonObject()) : null;
	}

	/** Objekte einer JSON-Liste; Nicht-Objekte werden übersprungen. */
	public List<Cfg> list(String path) {
		List<Cfg> out = new ArrayList<>();
		JsonElement e = find(path);
		if (e != null && e.isJsonArray()) {
			JsonArray array = e.getAsJsonArray();
			for (JsonElement item : array) {
				if (item.isJsonObject()) {
					out.add(new Cfg(item.getAsJsonObject()));
				}
			}
		}
		return out;
	}

	public Set<String> keys() {
		// Eigene Menge statt einer veraenderbaren Sicht auf die Schluessel des JSON-Objekts.
		return new LinkedHashSet<>(root.keySet());
	}

	public Set<String> keys(String path) {
		Cfg section = section(path);
		return section == null ? new LinkedHashSet<>() : new LinkedHashSet<>(section.root.keySet());
	}
}
