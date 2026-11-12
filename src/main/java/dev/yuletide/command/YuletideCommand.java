package dev.yuletide.command;

import java.util.Arrays;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;

/** Sammelbefehl {@code /yuletide <calendar|snow|santa|cracker|reload>}; leitet an die Einzelbefehle weiter. */
public final class YuletideCommand extends CommandBase {
	private final AdventCalendarCommand calendar = new AdventCalendarCommand();
	private final ChristmasCommand christmas = new ChristmasCommand();
	private final CrackerCommand cracker = new CrackerCommand();

	@Override
	protected int execute(CommandSourceStack source, String[] args) {
		if (args.length == 0) {
			reply(source, "usage.yuletide");
			return OK;
		}
		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		// calendar/ cracker sind neue Sammel-Praefixe und werden vor dem Weiterreichen entfernt.
		// ChristmasCommand erwartet hingegen "snow", "santa" oder "reload" selbst als args[0].
		switch (args[0].toLowerCase()) {
			case "calendar" -> calendar.execute(source, rest);
			case "cracker" -> cracker.execute(source, rest);
			case "snow", "santa", "reload" -> christmas.execute(source, args);
			default -> reply(source, "usage.yuletide");
		}
		return OK;
	}

	@Override
	protected List<String> suggest(CommandSourceStack source, String[] args) {
		if (args.length == 1) {
			return isOp(source)
				? List.of("calendar", "snow", "santa", "cracker", "reload")
				: List.of("calendar", "snow", "cracker");
		}
		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		// Dieselbe Argumentverschiebung wie bei execute, damit Tab-Vervollstaendigung
		// und tatsaechliche Ausfuehrung immer die gleichen Positionen sehen.
		return switch (args[0].toLowerCase()) {
			case "calendar" -> calendar.suggest(source, rest);
			case "cracker" -> cracker.suggest(source, rest);
			case "snow", "santa", "reload" -> christmas.suggest(source, args);
			default -> List.of();
		};
	}
}
