package com.spring.api.hub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(HubIntegrationTest.Containers.class)
class HubApplicationTests {

	@Test
	void contextLoads() {
	}

}
