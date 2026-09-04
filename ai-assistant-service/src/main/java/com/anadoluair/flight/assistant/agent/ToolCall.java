package com.anadoluair.flight.assistant.agent;

import java.util.Map;

/**
 * Modelin "şu aracı şu argümanlarla çağır" isteği.
 *
 * <p>Model aracı kendisi çalıştırmaz. Yalnızca hangisini istediğini söyler.
 * Çalıştırma bizim tarafımızda olur. Bu ayrım önemlidir: modelin sistemimizde
 * ne yapabileceğine biz karar veririz.
 */
public record ToolCall(String tool, Map<String, Object> arguments) {

    public String argument(String name) {
        Object value = arguments.get(name);
        return value == null ? null : String.valueOf(value);
    }
}
