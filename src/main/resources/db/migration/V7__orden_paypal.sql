-- Recuperación de pagos de PayPal: se guarda en la publicación pendiente el id de la última orden
-- creada. Así se puede consultar la orden y aprobar un pago que PayPal ya cobró aunque la captura
-- no haya llegado a confirmarse. Nulo para las publicaciones que nunca pasaron por PayPal.
ALTER TABLE publicacion ADD COLUMN orden_paypal VARCHAR(80) DEFAULT NULL;
