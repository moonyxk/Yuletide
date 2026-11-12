package dev.yuletide.gift;

/** Feste Geschenkarten; sie steuern das Einlesen und die spaetere Ausgabe im GiftManager. */
public enum GiftType {
	ITEM,
	COMMAND,
	/** Ohne Rechtesystem in Vanilla: setzt einen Scoreboard-Tag mit dem Namen des Rechts. */
	PERMISSION
}
