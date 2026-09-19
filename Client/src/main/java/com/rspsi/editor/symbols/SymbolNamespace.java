package com.rspsi.editor.symbols;

import java.util.Objects;

/**
 * Standard namespaces for symbolic identifier mapping across OSRS cache and server content.
 */
public enum SymbolNamespace {
    LOC("loc."),
    NPC("npc."),
    ITEM("item."),
    VARBIT("varbit."),
    VARP("varp."),
    VARC("varc."),
    INTERFACE("interface."),
    COMPONENT("component."),
    CLIENTSCRIPT("clientscript."),
    DB_TABLE("dbtable."),
    AREA("area."),
    SEQUENCE("seq."),
    SPOTANIM("spotanim.");

    private final String prefix;

    SymbolNamespace(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    /** Formats a bare symbol name into a canonical qualified symbol, e.g. "bank_booth" -> "loc.bank_booth". */
    public String qualify(String name) {
        Objects.requireNonNull(name, "name");
        return name.startsWith(prefix) ? name : prefix + name;
    }

    /** Strips the prefix if present, returning the bare symbol name. */
    public String unqualify(String name) {
        Objects.requireNonNull(name, "name");
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }
}
