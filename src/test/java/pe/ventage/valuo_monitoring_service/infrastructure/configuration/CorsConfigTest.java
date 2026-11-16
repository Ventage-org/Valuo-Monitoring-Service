package pe.ventage.valuo_monitoring_service.infrastructure.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CorsConfigTest {

    @Autowired
    private CorsFilter corsFilter;

    @Test
    @DisplayName("CORS Filter permite origen Angular http://localhost:4200 con credenciales")
    void corsFilter_allowsAngularOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/dashboard/overview");
        request.addHeader("Origin", "http://localhost:4200");
        request.addHeader("Access-Control-Request-Method", "GET");
        request.addHeader("Access-Control-Request-Headers", "Content-Type");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        corsFilter.doFilter(request, response, filterChain);

        assertEquals("http://localhost:4200", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("GET"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("POST"));
    }

    @Test
    @DisplayName("CORS Filter rechaza orígenes no autorizados")
    void corsFilter_rejectsUnauthorizedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/dashboard/overview");
        request.addHeader("Origin", "http://malicious-site.com");
        request.addHeader("Access-Control-Request-Method", "GET");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        corsFilter.doFilter(request, response, filterChain);

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }
}

