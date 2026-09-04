package com.anadoluair.flight.assistant.tool;

import com.anadoluair.flight.assistant.agent.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kullanılabilir araçları tutar ve çağrıları yürütür.
 *
 * <p>Buradaki iki davranış bilinçlidir ve testlerle sabitlenmiştir:
 *
 * <ol>
 *   <li><b>Bilinmeyen araç adı istisna fırlatmaz.</b> Model var olmayan bir araç
 *       uydurabilir; bu normaldir, hata değildir. Uygulamayı çökertmek yerine
 *       modele "böyle bir araç yok" diye geri yazarız, model kendini düzeltir.</li>
 *   <li><b>Araç patlarsa istisna dışarı sızmaz.</b> Uçuş servisi kapalı olabilir.
 *       Bu durumda da modele durumu anlatan bir metin döneriz.</li>
 * </ol>
 *
 * <p>Her iki halde de döngü devam eder. Tek bir başarısız araç çağrısı, kullanıcıya
 * hiç cevap verilmemesi anlamına gelmemeli.
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> byName;

    public ToolRegistry(List<Tool> tools) {
        this.byName = tools.stream()
                .collect(Collectors.toMap(tool -> tool.spec().name(), Function.identity()));
        log.info("Kayitli arac sayisi: {} -> {}", byName.size(), byName.keySet());
    }

    public List<ToolSpec> specs() {
        return byName.values().stream().map(Tool::spec).toList();
    }

    public String run(ToolCall call) {
        Tool tool = byName.get(call.tool());

        if (tool == null) {
            log.warn("Model olmayan bir araci istedi: {}", call.tool());
            return "'%s' diye bir arac yok. Kullanabilecegin araclar: %s"
                    .formatted(call.tool(), String.join(", ", byName.keySet()));
        }

        try {
            return tool.execute(call.arguments());
        } catch (RuntimeException e) {
            log.warn("Arac calisirken hata verdi: {} -> {}", call.tool(), e.toString());
            return "'%s' araci su anda calismadi: %s".formatted(call.tool(), e.getMessage());
        }
    }
}
