package com.example.jrfind.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// 모든 응답에 붙는 보안 헤더, 개발용 기능(H2 콘솔)의 기본 설정
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 화면에는_보안_헤더가_붙는다() throws Exception {
		mockMvc.perform(get("/items"))
			.andExpect(status().isOk())
			.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
			.andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
			.andExpect(header().string("X-Frame-Options", "DENY")) // 다른 사이트가 이 화면을 iframe 에 넣지 못하게
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void H2_콘솔은_기본으로_꺼져_있다() throws Exception {
		mockMvc.perform(get("/h2-console/"))
			.andExpect(status().isNotFound());
	}
}
