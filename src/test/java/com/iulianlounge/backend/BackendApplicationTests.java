package com.iulianlounge.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=dGVzdC1zZWNyZXQtcXVlLXRpZW5lLW1hcy1kZS0zMi1ieXRlcyEh")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
