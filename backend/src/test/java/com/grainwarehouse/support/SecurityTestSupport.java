package com.grainwarehouse.support;

import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.auth.repository.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Dùng cho test bảo mật: MockMvc KHÔNG có người dùng mặc định (khác DefaultTestUserConfig) → mọi request đi
 * đúng đường của trình duyệt: không cookie = chưa đăng nhập, không CSRF = bị chặn.
 */
public final class SecurityTestSupport {

    public static final String PASSWORD = "matkhau-dung-123";

    private SecurityTestSupport() {
    }

    public static MockMvc rawMockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    public static AppUser createUser(AppUserRepository repository, PasswordEncoder encoder, String username,
                                     boolean active, Role... roles) {
        AppUser user = new AppUser(username);
        user.setFullName("Người dùng " + username);
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setActive(active);
        user.replaceRoles(List.of(roles));
        return repository.save(user);
    }

    /** Cookie trình duyệt đang giữ sau một lần đăng nhập / gia hạn. */
    public record Session(Cookie access, Cookie refresh) {

        public static Session from(MvcResult result) {
            MockHttpServletResponse response = result.getResponse();
            return new Session(response.getCookie("GW_AT"), response.getCookie("GW_RT"));
        }
    }

    public static Session login(MockMvc mockMvc, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + username + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Đăng nhập thất bại: " + result.getResponse().getContentAsString());
        }
        return Session.from(result);
    }
}
