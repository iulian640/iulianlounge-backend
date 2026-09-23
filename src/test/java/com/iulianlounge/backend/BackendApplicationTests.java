package com.iulianlounge.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=test-secret-que-tiene-mas-de-32-bytes!!")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
