package com.anadoluair.flight.assistant.tool;

import java.util.Map;

/** Modelin çağırabileceği tek bir yetenek. */
public interface Tool {

    ToolSpec spec();

    /**
     * Aracı çalıştırır ve sonucu modele geri verilecek metin olarak döner.
     *
     * <p>Sonuç metin olmalı: model yalnızca metin okur. Bu yüzden nesneyi burada
     * okunabilir bir cümleye çeviririz, ham JSON göndermeyiz.
     */
    String execute(Map<String, Object> arguments);
}
