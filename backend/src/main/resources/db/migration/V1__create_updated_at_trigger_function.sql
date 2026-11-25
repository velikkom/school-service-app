-- Ortak "updated_at" otomatik güncelleme fonksiyonu.
-- Her tabloya ait CREATE TRIGGER ifadesi, ilgili tabloyu oluşturan migration dosyasında tanımlanır.
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
