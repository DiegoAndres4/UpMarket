package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.application.port.out.TransaccionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransaccionSpringAdapter implements TransaccionPort {

    private final TransactionTemplate plantilla;

    public TransaccionSpringAdapter(PlatformTransactionManager gestor) {
        this.plantilla = new TransactionTemplate(gestor);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return plantilla.execute(status -> trabajo.get());
    }
}
