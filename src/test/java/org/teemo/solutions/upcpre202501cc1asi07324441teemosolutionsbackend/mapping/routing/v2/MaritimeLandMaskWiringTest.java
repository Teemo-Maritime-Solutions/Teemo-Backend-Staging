package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import static org.assertj.core.api.Assertions.assertThat;

class MaritimeLandMaskWiringTest {
    @Test void springSelectsTheProductionConstructorWithTheRealLocalResource() {
        new ApplicationContextRunner().withBean(GeoUtils.class).withUserConfiguration(MaritimeLandMask.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(MaritimeLandMask.class);
            });
    }
}
