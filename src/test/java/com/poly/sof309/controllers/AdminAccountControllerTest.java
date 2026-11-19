package com.poly.sof309.controllers;

import com.petcare.controllers.DangXuatServlet;
import com.petcare.controllers.QuanLyTaiKhoanServlet;
import com.petcare.daos.AccountDAO;
import com.petcare.filter.AuthFilter;
import com.petcare.models.Users;
import com.petcare.services.AuthService;
import com.petcare.utils.SessionUtil;
import com.petcare.utils.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =========================================================================
 * [Y3] UNIT TEST CHO QUẢN LÝ TÀI KHOẢN ADMIN, PHÂN QUYỀN RBAC & ĐĂNG XUẤT
 * (QuanLyTaiKhoanServlet, AuthFilter, DangXuatServlet, SessionUtil).
 * =========================================================================
 */
public class AdminAccountControllerTest {

    private static String sharedHash;
    private FakeAccountDAO fakeAccountDAO;
    private QuanLyTaiKhoanServlet adminServlet;
    private Users adminUser;
    private Users customerUser;

    @BeforeAll
    static void initHash() {
        sharedHash = AuthService.hashPassword("Pass@1234");
    }

    @BeforeEach
    void setUp() {
        fakeAccountDAO = new FakeAccountDAO();
        adminUser = new Users(1, "Quản Trị Viên", "admin@petcare.vn", "0909000111",
                sharedHash, Users.ROLE_ADMIN, true, LocalDateTime.now());
        customerUser = new Users(2, "Khách Hàng A", "khach@petcare.vn", "0912345678",
                sharedHash, Users.ROLE_CUSTOMER, true, LocalDateTime.now());

        fakeAccountDAO.seed(adminUser);
        fakeAccountDAO.seed(customerUser);

        adminServlet = new QuanLyTaiKhoanServlet(fakeAccountDAO);
    }

    @Test
    @DisplayName("TC_ADMIN_AUTH_01: Chưa đăng nhập truy cập /admin/tai-khoan bị chuyển hướng về /login")
    void testAdminServletUnauthenticatedRedirectsLogin() throws ServletException, IOException {
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment");

        adminServlet.doGet(exchange.request(), exchange.response());

        assertEquals("/sof309-assignment/login", exchange.getRedirectedUrl());
    }

    @Test
    @DisplayName("TC_ADMIN_AUTH_02: Tài khoản CUSTOMER truy cập /admin/tai-khoan bị chuyển hướng về /home")
    void testAdminServletNonAdminRedirectsHome() throws ServletException, IOException {
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment")
                .withLoggedInUser(customerUser);

        adminServlet.doGet(exchange.request(), exchange.response());

        assertEquals("/sof309-assignment/home", exchange.getRedirectedUrl());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_ADMIN_CREATE_OK: Admin tạo tài khoản STAFF mới thành công và mật khẩu được băm BCrypt")
    void testAdminCreateStaffAccountSuccess() throws ServletException, IOException {
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "create")
                .withParam("fullName", "  Nhân Viên Spa  ")
                .withParam("email", "staff.spa@petcare.vn")
                .withParam("phone", "0933444555")
                .withParam("password", "Staff@1234")
                .withParam("role", Users.ROLE_STAFF);

        adminServlet.doPost(exchange.request(), exchange.response());

        assertEquals("/admin/quanLiTaiKhoan.jsp", exchange.getForwardedPath());
        assertEquals("Đã tạo tài khoản mới.", exchange.getRequestAttribute("successMessage"));
        Users created = fakeAccountDAO.findByEmail("staff.spa@petcare.vn");
        assertNotNull(created);
        assertEquals("Nhân Viên Spa", created.getFullName());
        assertEquals(Users.ROLE_STAFF, created.getRole());
        assertTrue(AuthService.verifyPassword("Staff@1234", created.getPasswordHash()));
    }

    @ParameterizedTest(name = "[{index}] Create invalid: name=''{0}'', email=''{1}'', role=''{3}''")
    @DisplayName("TC_ADMIN_CREATE_BOUNDARY: Kiểm tra biên tạo tài khoản mới của Admin")
    @CsvSource({
        "'', 'staff@petcare.vn', 'Staff@1234', 'STAFF', 'Vui lòng điền họ tên, email hợp lệ và mật khẩu tối thiểu 8 ký tự.'",
        "'Nhân Viên', '', 'Staff@1234', 'STAFF', 'Vui lòng điền họ tên, email hợp lệ và mật khẩu tối thiểu 8 ký tự.'",
        "'Nhân Viên', 'staff@petcare.vn', '1234567', 'STAFF', 'Vui lòng điền họ tên, email hợp lệ và mật khẩu tối thiểu 8 ký tự.'",
        "'Nhân Viên', 'email-khong-hop-le', 'Staff@1234', 'STAFF', 'Email không hợp lệ.'",
        "'Nhân Viên', 'staff@petcare.vn', 'Staff@1234', 'MANAGER', 'Vai trò không hợp lệ.'"
    })
    void testAdminCreateBoundaryValidation(String fullName, String email, String password,
                                           String role, String expectedError)
            throws ServletException, IOException {
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "create")
                .withParam("fullName", fullName)
                .withParam("email", email)
                .withParam("phone", "0933444555")
                .withParam("password", password)
                .withParam("role", role);

        adminServlet.doPost(exchange.request(), exchange.response());

        assertEquals("/admin/quanLiTaiKhoan.jsp", exchange.getForwardedPath());
        assertEquals(expectedError, exchange.getRequestAttribute("errorMessage"));
    }

    @Test
    @DisplayName("TC_ADMIN_UPDATE_OK: Admin cập nhật thông tin và nâng quyền tài khoản khác lên STAFF")
    void testAdminUpdateAccountSuccess() throws ServletException, IOException {
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "update")
                .withParam("userId", "2")
                .withParam("fullName", "Khách Hàng Nâng Quyền")
                .withParam("email", "staff.new@petcare.vn")
                .withParam("phone", "0912345000")
                .withParam("role", Users.ROLE_STAFF);

        adminServlet.doPost(exchange.request(), exchange.response());

        assertEquals("Đã lưu thông tin tài khoản.", exchange.getRequestAttribute("successMessage"));
        Users updated = fakeAccountDAO.findById(2);
        assertEquals("Khách Hàng Nâng Quyền", updated.getFullName());
        assertEquals(Users.ROLE_STAFF, updated.getRole());
    }

    @Test
    @DisplayName("TC_ADMIN_UPDATE_SELF_DEMOTE: Admin không thể tự bỏ quyền quản trị của chính mình")
    void testAdminCannotDemoteSelf() throws ServletException, IOException {
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "update")
                .withParam("userId", "1")
                .withParam("fullName", "Quản Trị Viên")
                .withParam("email", "admin@petcare.vn")
                .withParam("phone", "0909000111")
                .withParam("role", Users.ROLE_STAFF);

        adminServlet.doPost(exchange.request(), exchange.response());

        assertEquals("Không thể tự bỏ quyền quản trị của chính mình.",
                exchange.getRequestAttribute("errorMessage"));
        assertEquals(Users.ROLE_ADMIN, fakeAccountDAO.findById(1).getRole());
    }

    @Test
    @DisplayName("TC_ADMIN_TOGGLE_OK: Admin khóa và mở khóa tài khoản khách hàng thành công")
    void testAdminToggleActiveLockAndUnlock() throws ServletException, IOException {
        MockServletHelper.MockExchange lockExchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "toggleActive")
                .withParam("userId", "2")
                .withParam("active", "false");

        adminServlet.doPost(lockExchange.request(), lockExchange.response());
        assertEquals("Đã cập nhật trạng thái tài khoản.", lockExchange.getRequestAttribute("successMessage"));
        assertFalse(fakeAccountDAO.findById(2).isActive());

        MockServletHelper.MockExchange unlockExchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "toggleActive")
                .withParam("userId", "2")
                .withParam("active", "true");

        adminServlet.doPost(unlockExchange.request(), unlockExchange.response());
        assertEquals("Đã cập nhật trạng thái tài khoản.", unlockExchange.getRequestAttribute("successMessage"));
        assertTrue(fakeAccountDAO.findById(2).isActive());
    }

    @Test
    @DisplayName("TC_ADMIN_TOGGLE_SELF_LOCK: Admin không thể tự khóa tài khoản của chính mình")
    void testAdminCannotLockSelf() throws ServletException, IOException {
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "toggleActive")
                .withParam("userId", "1")
                .withParam("active", "false");

        adminServlet.doPost(exchange.request(), exchange.response());

        assertEquals("Không thể tự khóa tài khoản của chính mình.",
                exchange.getRequestAttribute("errorMessage"));
        assertTrue(fakeAccountDAO.findById(1).isActive());
    }

    @Test
    @DisplayName("TC_ADMIN_TOGGLE_NOT_FOUND: Báo lỗi khi khóa tài khoản không tồn tại")
    void testAdminToggleNonExistentUserThrowsError() throws ServletException, IOException {
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "toggleActive")
                .withParam("userId", "999")
                .withParam("active", "false");

        adminServlet.doPost(exchange.request(), exchange.response());

        assertEquals("Tài khoản không tồn tại.", exchange.getRequestAttribute("errorMessage"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_ADMIN_RESET_PW: Admin đặt lại mật khẩu từ chối < 8 ký tự và thành công >= 8 ký tự")
    void testAdminResetPasswordValidationAndSuccess() throws ServletException, IOException {
        MockServletHelper.MockExchange invalidExchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "resetPassword")
                .withParam("userId", "2")
                .withParam("newPassword", "1234567");

        adminServlet.doPost(invalidExchange.request(), invalidExchange.response());
        assertEquals("Mật khẩu mới phải có tối thiểu 8 ký tự.",
                invalidExchange.getRequestAttribute("errorMessage"));

        MockServletHelper.MockExchange validExchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "resetPassword")
                .withParam("userId", "2")
                .withParam("newPassword", "Reset@2026");

        adminServlet.doPost(validExchange.request(), validExchange.response());
        assertEquals("Đã đặt lại mật khẩu.", validExchange.getRequestAttribute("successMessage"));
        assertTrue(AuthService.verifyPassword("Reset@2026", fakeAccountDAO.findById(2).getPasswordHash()));
    }

    @Test
    @DisplayName("TC_ADMIN_INVALID_ACTION_AND_ID: Báo lỗi khi action lạ hoặc userId không phải số")
    void testAdminInvalidActionAndMalformedUserId() throws ServletException, IOException {
        MockServletHelper.MockExchange badAction = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "deleteAll");

        adminServlet.doPost(badAction.request(), badAction.response());
        assertEquals("Hành động không hợp lệ.", badAction.getRequestAttribute("errorMessage"));

        MockServletHelper.MockExchange badId = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "toggleActive")
                .withParam("userId", "abc")
                .withParam("active", "false");

        adminServlet.doPost(badId.request(), badId.response());
        assertEquals("Thiếu hoặc sai mã tài khoản.", badId.getRequestAttribute("errorMessage"));
    }

    @ParameterizedTest(name = "[{index}] SQL error code {0} -> thông báo Admin")
    @DisplayName("TC_ADMIN_SQL_MAP: QuanLyTaiKhoanServlet ánh xạ mã lỗi SQL Server sang thông báo tiếng Việt")
    @CsvSource({
        "2627, 'Email đã được sử dụng bởi tài khoản khác.'",
        "2601, 'Email đã được sử dụng bởi tài khoản khác.'",
        "229, 'Tài khoản SQL ''petcare_backend'' chưa được cấp đủ quyền trên bảng dbo.Users.'",
        "50000, 'Không thể lưu thay đổi. Vui lòng thử lại.'"
    })
    void testAdminServletSqlErrorTranslation(int sqlErrorCode, String expectedMessage)
            throws ServletException, IOException {
        fakeAccountDAO.sqlExceptionOnWrite = new SQLException("SQL error", "23000", sqlErrorCode);

        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(adminUser)
                .withParam("action", "create")
                .withParam("fullName", "User Test")
                .withParam("email", "test@petcare.vn")
                .withParam("phone", "0912345099")
                .withParam("password", "Pass@1234")
                .withParam("role", Users.ROLE_CUSTOMER);

        adminServlet.doPost(exchange.request(), exchange.response());

        assertEquals("/admin/quanLiTaiKhoan.jsp", exchange.getForwardedPath());
        assertEquals(expectedMessage, exchange.getRequestAttribute("errorMessage"));
    }

    @ParameterizedTest(name = "[{index}] Path=''{0}'', Role=''{1}'' -> cho phép={2}, status={3}")
    @DisplayName("TC_FILTER_RBAC: Kiểm tra AuthFilter phân quyền theo đường dẫn /admin/*, /staff/*, /customer/*")
    @CsvSource({
        "/admin/tai-khoan, NONE, false, 302",
        "/admin/tai-khoan, CUSTOMER, false, 403",
        "/admin/tai-khoan, STAFF, false, 403",
        "/admin/tai-khoan, ADMIN, true, 200",
        "/staff/lich-hen, CUSTOMER, false, 403",
        "/staff/lich-hen, STAFF, true, 200",
        "/staff/lich-hen, ADMIN, true, 200",
        "/customer/dat-lich, CUSTOMER, true, 200",
        "/customer/dat-lich, STAFF, true, 200",
        "/customer/dat-lich, ADMIN, true, 200",
        "/customer/dat-lich, GUEST, false, 403"
    })
    void testAuthFilterRoleBasedAccessControl(String path, String role, boolean expectedAllowed, int expectedStatus)
            throws ServletException, IOException {
        AuthFilter filter = new AuthFilter();
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment")
                .withRequestPath(path);

        if (!"NONE".equals(role)) {
            Users user = new Users(10, "Role User", "role@petcare.vn", "0911222333",
                    sharedHash, role, true, LocalDateTime.now());
            exchange.withLoggedInUser(user);
        }

        filter.doFilter(exchange.request(), exchange.response(), exchange.filterChain());

        assertEquals(expectedAllowed, exchange.isChainProceeded());
        assertEquals(expectedStatus, exchange.getStatus());
        if (expectedStatus == HttpServletResponse.SC_FOUND) {
            assertEquals("/sof309-assignment/login", exchange.getRedirectedUrl());
        } else if (expectedStatus == HttpServletResponse.SC_FORBIDDEN) {
            assertEquals(HttpServletResponse.SC_FORBIDDEN, exchange.getErrorStatus());
        }
    }

    @Test
    @DisplayName("TC_LOGOUT_01: DangXuatServlet hủy phiên đăng nhập và chuyển hướng về /login")
    void testLogoutClearsSessionAndRedirectsLogin() throws ServletException, IOException {
        DangXuatServlet servlet = new DangXuatServlet();
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment")
                .withLoggedInUser(customerUser);

        assertTrue(SessionUtil.isLoggedIn(exchange.request()));

        servlet.doGet(exchange.request(), exchange.response());

        assertTrue(exchange.isSessionInvalidated());
        assertFalse(SessionUtil.isLoggedIn(exchange.request()));
        assertEquals("/sof309-assignment/login", exchange.getRedirectedUrl());
    }

    private static final class FakeAccountDAO implements AccountDAO {
        private final Map<Integer, Users> storage = new LinkedHashMap<>();
        private int nextId = 10;
        SQLException sqlExceptionOnWrite;

        void seed(Users user) {
            storage.put(user.getUserId(), copy(user));
            if (user.getUserId() >= nextId) {
                nextId = user.getUserId() + 1;
            }
        }

        @Override
        public Users findById(int userId) {
            Users found = storage.get(userId);
            return found != null ? copy(found) : null;
        }

        @Override
        public Users findByEmail(String email) {
            String target = ValidationUtil.normalizeEmail(email);
            for (Users user : storage.values()) {
                if (ValidationUtil.normalizeEmail(user.getEmail()).equals(target)) {
                    return copy(user);
                }
            }
            return null;
        }

        @Override
        public Users findByPhone(String phone) {
            String target = ValidationUtil.normalizePhone(phone);
            for (Users user : storage.values()) {
                if (ValidationUtil.normalizePhone(user.getPhone()).equals(target)) {
                    return copy(user);
                }
            }
            return null;
        }

        @Override
        public Users findByIdentifier(String identifier) {
            if (identifier == null) {
                return null;
            }
            String normEmail = ValidationUtil.normalizeEmail(identifier);
            String normPhone = ValidationUtil.normalizePhone(identifier);
            for (Users user : storage.values()) {
                if (ValidationUtil.normalizeEmail(user.getEmail()).equals(normEmail)
                        || ValidationUtil.normalizePhone(user.getPhone()).equals(normPhone)) {
                    return copy(user);
                }
            }
            return null;
        }

        @Override
        public boolean existsByEmail(String email) {
            return findByEmail(email) != null;
        }

        @Override
        public boolean existsByPhone(String phone) {
            return findByPhone(phone) != null;
        }

        @Override
        public Users create(Users user) throws SQLException {
            if (sqlExceptionOnWrite != null) {
                throw sqlExceptionOnWrite;
            }
            Users saved = copy(user);
            saved.setUserId(nextId++);
            if (saved.getCreatedAt() == null) {
                saved.setCreatedAt(LocalDateTime.now());
            }
            storage.put(saved.getUserId(), saved);
            return copy(saved);
        }

        @Override
        public boolean updateRoleAndStatus(int userId, String role, boolean isActive) throws SQLException {
            if (sqlExceptionOnWrite != null) {
                throw sqlExceptionOnWrite;
            }
            Users target = storage.get(userId);
            if (target == null) {
                return false;
            }
            target.setRole(role);
            target.setActive(isActive);
            return true;
        }

        @Override
        public boolean updateProfile(Users user) throws SQLException {
            if (sqlExceptionOnWrite != null) {
                throw sqlExceptionOnWrite;
            }
            Users target = storage.get(user.getUserId());
            if (target == null) {
                return false;
            }
            target.setFullName(user.getFullName());
            target.setPhone(user.getPhone());
            target.setEmail(user.getEmail());
            target.setRole(user.getRole());
            return true;
        }

        @Override
        public boolean updatePassword(int userId, String passwordHash) throws SQLException {
            if (sqlExceptionOnWrite != null) {
                throw sqlExceptionOnWrite;
            }
            Users target = storage.get(userId);
            if (target == null) {
                return false;
            }
            target.setPasswordHash(passwordHash);
            return true;
        }

        @Override
        public List<Users> findAll() {
            List<Users> list = new ArrayList<>();
            for (Users user : storage.values()) {
                list.add(copy(user));
            }
            return list;
        }

        private static Users copy(Users source) {
            return new Users(
                    source.getUserId(),
                    source.getFullName(),
                    source.getEmail(),
                    source.getPhone(),
                    source.getPasswordHash(),
                    source.getRole(),
                    source.isActive(),
                    source.getCreatedAt()
            );
        }
    }
}
