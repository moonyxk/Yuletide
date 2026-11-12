package dev.yuletide.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Wandelt Legacy-Farbcodes (&amp; und §, plus &amp;#rrggbb) in Components um und setzt Platzhalter nachträglich ein. */
public final class Text {
	private Text() {
	}

	public static Component legacy(String input) {
		return legacy(input, Map.of());
	}

	/**
	 * Platzhalter werden erst nach dem Parsen als reiner Text eingesetzt,
	 * damit Farbcodes in Spielernamen o. Ä. nicht interpretiert werden.
	 */
	public static Component legacy(String input, Map<String, String> placeholders) {
		// Kleine Parser-Zustandsmaschine: style gilt fuer den aktuell gesammelten Text
		// im buffer; result enthaelt bereits abgeschlossene, formatierte Textteile.
		MutableComponent result = Component.empty();
		Style style = Style.EMPTY;
		StringBuilder buffer = new StringBuilder();
		int i = 0;
		while (i < input.length()) {
			char c = input.charAt(i);
			if ((c == '&' || c == '§') && i + 1 < input.length()) {
				if (input.charAt(i + 1) == '#' && i + 8 <= input.length() && isHex(input, i + 2, i + 8)) {
					// Erst Text im ALTEN Stil abschliessen, dann auf die neue RGB-Farbe umschalten.
					flush(result, buffer, style, placeholders);
					style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(input.substring(i + 2, i + 8), 16)));
					i += 8;
					// Das gesamte Farbtoken wurde verarbeitet, nicht noch einmal als normalen Text lesen.
					continue;
				}
				ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(input.charAt(i + 1)));
				if (format != null) {
					flush(result, buffer, style, placeholders);
					style = apply(style, format);
					i += 2;
					continue;
				}
			}
			// Auch unbekannte oder unvollstaendige Farbcodes bleiben als normaler Text erhalten.
			buffer.append(c);
			i++;
		}
		flush(result, buffer, style, placeholders);
		return result;
	}

	public static List<Component> legacyLines(List<String> lines) {
		List<Component> out = new ArrayList<>();
		for (String line : lines) {
			out.add(legacy(line));
		}
		return out;
	}

	private static Style apply(Style style, ChatFormatting format) {
		if (format == ChatFormatting.RESET) {
			return Style.EMPTY;
		}
		if (format.isColor()) {
			// Legacy-Farbwechsel setzen vorherige Effekte wie Fett/Kursiv zurueck.
			// Reine Formatcodes ergaenzen dagegen den bestehenden Stil.
			return Style.EMPTY.withColor(format);
		}
		return style.applyFormat(format);
	}

	private static boolean isHex(String s, int from, int to) {
		for (int i = from; i < to; i++) {
			if (Character.digit(s.charAt(i), 16) < 0) {
				return false;
			}
		}
		return true;
	}

	/** Haengt einen fertigen Textabschnitt an und leert den Puffer fuer den naechsten Stil. */
	private static void flush(MutableComponent result, StringBuilder buffer, Style style, Map<String, String> placeholders) {
		if (buffer.isEmpty()) {
			return;
		}
		String text = buffer.toString();
		buffer.setLength(0);
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			text = text.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		// literal interpretiert eingesetzte Werte nicht erneut als Farbcode oder Minecraft-Uebersetzungsschluessel.
		result.append(Component.literal(text).withStyle(style));
	}
}
