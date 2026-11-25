package com.okulservisi.backend.security.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.attendance.controller.AttendanceEventController;
import com.okulservisi.backend.auth.api.AuthApiPaths;
import com.okulservisi.backend.driver.controller.DriverController;
import com.okulservisi.backend.hostess.controller.HostessController;
import com.okulservisi.backend.route.controller.RouteController;
import com.okulservisi.backend.trip.controller.TripController;
import com.okulservisi.backend.user.controller.UserController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Bu bir birim testidir (Spring context/Testcontainers gerektirmez) — API yolu sabitleri + özel
 * yetki annotation'ları refactor'ünün (bkz. docs/ARCHITECTURE.md "API yolu sabitleri"/"Yetki
 * annotation'ları") davranışı DEĞİŞTİRMEDİĞİNİ doğrular: {@code @AdminOnly} vb. özel annotation'lar
 * {@code @PreAuthorize}'ı meta-annotation olarak taşır; {@link AnnotatedElementUtils#findMergedAnnotation}
 * Spring'in method-security çözümlemesiyle AYNI şekilde meta-annotation'ların içinden gerçek
 * {@code @PreAuthorize} değerini bulur. Metot seviyesinde annotation yoksa sınıf seviyesine
 * (Spring'in gerçek önceliğiyle AYNI sırada: metot ÖNCE, sonra sınıf) elle düşülür.
 *
 * <p>Tüm 104 endpoint'in tek tek tekrarı YERİNE, refactor'ün riskli noktalarını (meta-annotation
 * çözümlemesi + sınıf/metot override önceliği + tek seferlik ham {@code @PreAuthorize}'ın
 * dokunulmadan kalması + {@code SecurityConfig}'in {@code permitAll} listesinin {@code
 * AuthController} mapping'leriyle hâlâ birebir aynı olması) temsilen doğrular.
 */
class SecurityAnnotationResolutionTest {

    private static String resolvedExpression(Class<?> controller, String methodName) throws NoSuchMethodException {
        Method method = findMethod(controller, methodName);
        PreAuthorize methodLevel = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (methodLevel != null) {
            return methodLevel.value();
        }
        PreAuthorize classLevel = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        return classLevel == null ? null : classLevel.value();
    }

    private static Method findMethod(Class<?> controller, String methodName) {
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Metot bulunamadı: " + controller.getSimpleName() + "#" + methodName);
    }

    @Test
    void adminOnly_resolvesToHasRoleAdmin_atMethodLevel() throws Exception {
        assertThat(resolvedExpression(DriverController.class, "update")).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void adminOnly_resolvesToHasRoleAdmin_whenAppliedAtClassLevel() throws Exception {
        // UserController: @AdminOnly SADECE sınıf seviyesinde var, hiçbir metotta override yok.
        assertThat(resolvedExpression(UserController.class, "getById")).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void adminDriverOrHostess_resolvesToExpectedExpression_whenAppliedAtClassLevel() throws Exception {
        // AttendanceEventController: @AdminDriverOrHostess sınıf seviyesinde, hiçbir metotta override yok.
        assertThat(resolvedExpression(AttendanceEventController.class, "boarding"))
                .isEqualTo("hasAnyRole('ADMIN','DRIVER','HOSTESS')");
    }

    @Test
    void methodLevelAnnotation_overridesClassLevelAnnotation() throws Exception {
        // RouteController: sınıf seviyesi @AdminOnly, ama list()/getById() metot seviyesinde
        // @AdminDriverOrHostess ile override edilir — Spring'in metot-önce-sınıf önceliği KORUNMALI.
        assertThat(resolvedExpression(RouteController.class, "list")).isEqualTo("hasAnyRole('ADMIN','DRIVER','HOSTESS')");
        // create() metodunda override YOK — sınıf seviyesindeki @AdminOnly geçerli kalmalı.
        assertThat(resolvedExpression(RouteController.class, "create")).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void anyRole_resolvesToAllFiveDefinedRoles() throws Exception {
        assertThat(resolvedExpression(TripController.class, "list"))
                .isEqualTo("hasAnyRole('ADMIN','DRIVER','HOSTESS','PARENT','STUDENT')");
    }

    @Test
    void singleUseExpression_isLeftAsRawPreAuthorize_notConvertedToSharedAnnotation() throws Exception {
        // hasRole('HOSTESS') projede SADECE burada kullanılıyor - tekrar eden bir kural olmadığı için
        // paylaşılan bir annotation'a dönüştürülmedi (bkz. docs/ARCHITECTURE.md "Yetki annotation'ları").
        assertThat(resolvedExpression(HostessController.class, "me")).isEqualTo("hasRole('HOSTESS')");
    }

    @Test
    void securityConfigPermitAllPaths_stillMatchAuthControllerMappings() {
        // SecurityConfig artık AuthApiPaths sabitlerini kullanıyor; bu test, o bağlamanın
        // (örn. yanlış bir sabit referansı) sessizce AuthController'ın gerçek mapping'lerinden
        // sapmadığını doğrular (bkz. CLAUDE talimatı: "wildcard pattern'lerinin endpoint
        // mapping'leriyle aynı anlama geldiğini varsayma").
        assertThat(AuthApiPaths.AUTH_BASE + AuthApiPaths.LOGIN).isEqualTo("/api/auth/login");
        assertThat(AuthApiPaths.AUTH_BASE + AuthApiPaths.REFRESH).isEqualTo("/api/auth/refresh");
        assertThat(AuthApiPaths.AUTH_BASE + AuthApiPaths.FORGOT_PASSWORD).isEqualTo("/api/auth/forgot-password");
        assertThat(AuthApiPaths.AUTH_BASE + AuthApiPaths.RESET_PASSWORD).isEqualTo("/api/auth/reset-password");
        assertThat(AuthApiPaths.AUTH_BASE + AuthApiPaths.REGISTER_REQUESTS).isEqualTo("/api/auth/register-requests");
        assertThat(AuthApiPaths.AUTH_BASE + AuthApiPaths.REGISTER_REQUESTS_VERIFY_EMAIL)
                .isEqualTo("/api/auth/register-requests/verify-email");
        assertThat(AuthApiPaths.AUTH_BASE + AuthApiPaths.SOCIAL_LOGIN).isEqualTo("/api/auth/social-login");
    }
}
