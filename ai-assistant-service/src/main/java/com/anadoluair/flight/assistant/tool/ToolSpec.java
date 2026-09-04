package com.anadoluair.flight.assistant.tool;

import java.util.Map;

/**
 * Bir aracın modele tanıtımı.
 *
 * <p>Model kodu görmez. Yalnızca bu tanımı görür ve buna bakarak hangi aracı
 * çağıracağına karar verir. Bu yüzden {@code description} alanı bir yorum satırı
 * değil, işlevsel bir metindir: kötü yazılırsa model yanlış aracı seçer.
 *
 * @param name        aracın adı, model bu adı geri gönderir
 * @param description ne işe yaradığı, modelin okuyacağı açıklama
 * @param parameters  argüman adı -> o argümanın açıklaması
 */
public record ToolSpec(String name, String description, Map<String, String> parameters) {
}
