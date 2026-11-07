package com.upmarket.catalogo.application.port.out;

import java.util.function.Supplier;

public interface TransaccionPort {

    <T> T ejecutar(Supplier<T> trabajo);
}
