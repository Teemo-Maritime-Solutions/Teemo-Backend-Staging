package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.seed.roles.enabled=false",
		"app.seed.mapping.enabled=false",
		"app.route-graph.warmup.enabled=false",
		"routing.maritime.gfw.enabled=false"
})
class UpcPre202501Cc1asi07324441TeemoSolutionsBackEndApplicationTests {

	@Test
	void contextLoads() {
	}

}
