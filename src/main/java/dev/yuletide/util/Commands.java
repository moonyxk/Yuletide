package dev.yuletide.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fuehrt konfigurierte Belohnungsbefehle zentral aus.
 * Die Konfiguration ist eine Vertrauensgrenze: Die Befehle erhalten die Rechte der Serverkonsole.
 */
public final class Commands {
	private Commands() {
	}

	/** Führt den Befehl als Konsole aus; {@code {player}} wird durch den Spielernamen ersetzt. */
	public static void runAsConsole(MinecraftServer server, String command, ServerPlayer player) {
		String resolved = command.replace("{player}", player.getGameProfile().name()).trim();
		if (resolved.startsWith("/")) {
			// In der Konfiguration sind beide Schreibweisen erlaubt; der Dispatcher bekommt keinen fuehrenden Slash.
			resolved = resolved.substring(1);
		}
		// Die Befehlsquelle ist absichtlich der Server und NICHT der beschenkte Spieler.
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), resolved);
	}
}
