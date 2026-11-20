package com.upmarket.catalogo.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CloudinaryConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(CloudinaryConfig.class);

    @Test
    void fallaAlArrancarSiSeEligeCloudinarySinCredenciales() {
        runner.withPropertyValues("upmarket.fotos.proveedor=cloudinary").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("faltan credenciales");
        });
    }

    @Test
    void creaElClienteConLasTresCredenciales() {
        runner.withPropertyValues(
                        "upmarket.fotos.proveedor=cloudinary",
                        "cloudinary.cloud-name=demo",
                        "cloudinary.api-key=llave",
                        "cloudinary.api-secret=secreto")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Cloudinary.class);
                });
    }

    @Test
    void noCreaElClienteCuandoElProveedorEsLocal() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Cloudinary.class);
        });
    }
}
