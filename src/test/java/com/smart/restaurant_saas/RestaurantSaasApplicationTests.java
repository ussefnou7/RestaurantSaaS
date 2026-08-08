package com.smart.restaurant_saas;

import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RestaurantSaasApplicationTests {

	@Autowired
	private PhysicalCountService physicalCountService;

	@Test
	void contextLoads() {
	}

	@Test
	void physicalCountServiceIsRegistered() {
		org.assertj.core.api.Assertions.assertThat(physicalCountService).isNotNull();
	}

}
