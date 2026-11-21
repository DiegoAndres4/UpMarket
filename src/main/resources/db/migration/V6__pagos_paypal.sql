-- Pago con PayPal: se conserva la tarifa en COP (monto) y se guarda el monto realmente cobrado
-- en USD y el proveedor que aprobó el pago. Nulos para los pagos simulados existentes.
ALTER TABLE pago ADD COLUMN monto_usd DECIMAL(11, 2) DEFAULT NULL;
ALTER TABLE pago ADD COLUMN proveedor VARCHAR(30) DEFAULT NULL;
