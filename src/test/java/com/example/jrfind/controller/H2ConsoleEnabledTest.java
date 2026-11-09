package com.example.jrfind.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// 개발할 때 H2 콘솔을 켜면 (JRFIND_H2_CONSOLE=true): 콘솔은 열리고, 나머지 화면의 보안 설정은 그대로
@SpringBootTest(properties = "spring.h2.console.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class H2ConsoleEnabledTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 콘솔은_자체_화면을_쓸_수_있고_나머지_화면은_CSP_그대로() throws Exception {
		// 콘솔은 자체 스크립트·iframe 을 쓰므로 CSP 를 붙이지 않고, 같은 사이트 안의 iframe 은 허용
		// (콘솔 화면 자체는 H2 의 서블릿이 그려서 MockMvc 로는 열 수 없다. 여기서는 보안 설정만 확인)
		mockMvc.perform(get("/h2-console/"))
			.andExpect(header().doesNotExist("Content-Security-Policy"))
			.andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
		mockMvc.perform(get("/items"))
			.andExpect(header().exists("Content-Security-Policy"))
			.andExpect(header().string("X-Frame-Options", "DENY"));
	}
}
