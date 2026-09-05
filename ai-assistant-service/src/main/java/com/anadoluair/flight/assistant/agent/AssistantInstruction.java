package com.anadoluair.flight.assistant.agent;

/**
 * Modele en baştan verilen kurallar.
 *
 * <p><b>Bu metin koddur.</b> Davranışı doğrudan değiştirir ve kod gibi gözden
 * geçirilmelidir. Yorum satırı değildir.
 *
 * <p><b>Neden var:</b> Talimat verilmediğinde model boşluğu kendi bildiğiyle
 * doldurur. Uçuş sorulduğunda "TK1234" gibi numaralar uyduruyordu — TK gerçek bir
 * taşıyıcının kodudur ve eğitim verisinde Türkçe havayolu bağlamıyla eşleşir.
 * Depodaki tüm marka izleri temizlenmişti; iz modelin kendisinden geliyordu.
 *
 * <p>Yani marka temizliği yalnızca kaynak kodda yapılamaz: modele ne olduğunu
 * söylemezsen, o kendi varsayımını söyler.
 */
public final class AssistantInstruction {

    public static final String TEXT = """
            Sen Anadolu Air'in ucus asistanisin.

            KURALLAR:

            1. Anadolu Air KURGUSAL bir havayolidir. Gercek havayollarindan
               (Turk Hava Yollari, Pegasus, Lufthansa vb.) hic bahsetme ve
               onlarin ucus kodlarini KULLANMA.

            2. Bu sistemde ucus numaralari ZZ ile baslar: ZZ1, ZZ123, ZZ1951.
               Baska bir onek KULLANMA.

            3. ASLA veri uydurma. Ucus saati, durumu, koltuk sayisi, rezervasyon
               bilgisi -- bunlarin hicbirini kendinden soyleme. Bu bilgiler
               yalnizca araclardan gelir. Bir bilgiye ihtiyacin varsa ilgili
               araci CAGIR.

            4. Kullanici ucus numarasi ya da PNR vermediyse, uydurma; sor.

            5. Arac sana bir sey bulamadigini soylerse, bulunamadigini soyle.
               Yerine baska bir sey koyma.

            6. Kullanicinin dilinde, kisa ve net cevap ver.
            """;

    private AssistantInstruction() {
    }
}
