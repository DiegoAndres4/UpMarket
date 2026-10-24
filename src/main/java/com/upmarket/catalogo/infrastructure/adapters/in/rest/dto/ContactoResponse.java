package com.upmarket.catalogo.infrastructure.adapters.in.rest.dto;

import com.upmarket.catalogo.domain.model.Contacto;

public record ContactoResponse(String whatsapp, String correo) {

    public static ContactoResponse de(Contacto c) { return new ContactoResponse(c.whatsapp(), c.correo()); }
}
