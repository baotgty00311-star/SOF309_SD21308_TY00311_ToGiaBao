package com.poly.sof309.controllers;

import com.petcare.controllers.LoginServlet;
import com.petcare.controllers.ProfileServlet;
import com.petcare.controllers.RegisterServlet;
import com.petcare.daos.AccountDAO;
import com.petcare.models.Users;
import com.petcare.services.AuthService;
import com.petcare.utils.SessionUtil;
import com.petcare.utils.ValidationUtil;
import jakarta.servlet.ServletException;
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
 * [Y3] UNIT TEST CHO NGHIỆP VỤ XÁC THỰC & TÀI KHOẢN (AuthService & ValidationUtil)
 * Áp dụng @ParameterizedTest, @CsvSource, assertThrows() và @Timeout.
 * =========================================================================
 */
public class AuthControllerTest {

    private static String sharedPasswordHash;
    private FakeAccountDAO fakeAccountDAO;
    private AuthService authService;

    @BeforeAll
    static void initHash() {
        sharedPasswordHash = AuthService.hashPassword("Pass@1234");
    }

    @BeforeEach
    void setUp() {
        fakeAccountDAO = new FakeAccountDAO();
        fakeAccountDAO.seed(createUser(1, "Nguyễn Văn An", "khachhang@petcare.vn", "0912345678",
                sharedPasswordHash, Users.ROLE_CUSTOMER, true));
        fakeAccountDAO.seed(createUser(2, "Quản trị viên", "admin@petcare.vn", "0909000111",
                sharedPasswordHash, Users.ROLE_ADMIN, true));
        fakeAccountDAO.seed(createUser(3, "Tài khoản bị khóa", "locked@petcare.vn", "0988777666",
                sharedPasswordHash, Users.ROLE_CUSTOMER, false));

        authService = new AuthService(fakeAccountDAO);
    }

    private static Users createUser(int id, String fullName, String email, String phone,
                                    String passwordHash, String role, boolean active) {
        return new Users(id, fullName, email, phone, passwordHash, role, active, LocalDateTime.now());
    }

    @ParameterizedTest(name = "[{index}] SĐT=''{0}'' -> hợp lệ={1}, chuẩn hóa=''{2}''")
    @DisplayName("TC_VAL_PHONE: Kiểm tra chuẩn hóa và hợp lệ số điện thoại với dữ liệu biên")
    @CsvSource({
        "'', false, ''",
        "'abc12345', false, 'abc12345'",
        "'09123', false, '09123'",
        "'0912345678', true, '0912345678'",
        "'+84 912-345-678', true, '0912345678'"
    })
    void testPhoneValidationAndNormalization(String rawPhone, boolean expectedValid, String expectedNormalized) {
        assertEquals(expectedNormalized, ValidationUtil.normalizePhone(rawPhone));
        assertEquals(expectedValid, ValidationUtil.isValidPhone(rawPhone));
    }

    @ParameterizedTest(name = "[{index}] Email=''{0}'' -> hợp lệ={1}")
    @DisplayName("TC_VAL_EMAIL: Kiểm tra định dạng email với nhiều bộ dữ liệu")
    @CsvSource({
        "'', false",
        "'nguyenvana.petcare.vn', false",
        "'nguyenvana@localhost', false",
        "'khachhang@petcare.vn', true"
    })
    void testEmailValidation(String rawEmail, boolean expectedValid) {
        assertEquals(expectedValid, ValidationUtil.isValidEmail(rawEmail));
    }

    @ParameterizedTest(name = "[{index}] Đăng ký không hợp lệ: name=''{0}'', phone=''{1}'', email=''{2}''")
    @DisplayName("TC_REG_BOUNDARY: Kiểm tra biên form đăng ký ném IllegalArgumentException")
    @CsvSource({
        "'', '0912345999', 'new@petcare.vn', 'Pass@1234', 'Vui lòng nhập họ và tên.'",
        "'Tô Gia Bảo', '09123', 'new@petcare.vn', 'Pass@1234', 'Số điện thoại không hợp lệ'",
        "'Tô Gia Bảo', '0912345999', 'email-sai', 'Pass@1234', 'Địa chỉ Email không đúng định dạng.'",
        "'Tô Gia Bảo', '0912345999', 'new@petcare.vn', '1234567', 'Mật khẩu tạo mới phải có tối thiểu 8 ký tự.'"
    })
    void testRegisterValidationBoundaries(String fullName, String phone, String email,
                                          String password, String expectedMessageFragment) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.registerCustomer(fullName, phone, email, password));
        assertTrue(ex.getMessage().contains(expectedMessageFragment),
                "Thông báo lỗi thực tế: " + ex.getMessage());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_REG_01: Đăng ký thành công với mật khẩu biên 8 ký tự")
    void testRegisterCustomerSuccessBoundary8Chars() throws SQLException {
        Users created = authService.registerCustomer("  Tô Gia Bảo  ", "+84 912-345-777",
                "BaoTG@PetCare.vn", "Pass@123");

        assertNotNull(created);
        assertTrue(created.getUserId() > 0);
        assertEquals("Tô Gia Bảo", created.getFullName());
        assertEquals("0912345777", created.getPhone());
        assertEquals("baotg@petcare.vn", created.getEmail());
        assertEquals(Users.ROLE_CUSTOMER, created.getRole());
        assertTrue(created.isActive());
        assertTrue(AuthService.verifyPassword("Pass@123", created.getPasswordHash()));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_REG_02: Đăng ký bỏ trống email tự sinh email theo SĐT")
    void testRegisterCustomerBlankEmailAutoGenerates() throws SQLException {
        Users created = authService.registerCustomer("Khách Mới", "0912345888", "   ", "Pass@1234");

        assertNotNull(created);
        assertEquals("0912345888@petcare.vn", created.getEmail());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_AUTH_01: Đăng nhập thành công bằng SĐT và Email hợp lệ")
    void testAuthenticateWithPhoneAndEmailSuccess() throws SQLException {
        Users byPhone = authService.authenticate("0912345678", "Pass@1234");
        assertEquals(1, byPhone.getUserId());
        assertTrue(byPhone.isCustomer());

        Users byEmail = authService.authenticate("admin@petcare.vn", "Pass@1234");
        assertEquals(2, byEmail.getUserId());
        assertTrue(byEmail.isAdmin());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_PROF_01: Cập nhật hồ sơ và đổi mật khẩu thành công")
    void testUpdateProfileAndChangePasswordSuccess() throws SQLException {
        Users updated = authService.updateProfile(1, "Nguyễn Văn An Updated", "0912345679", "an.updated@petcare.vn");
        assertEquals("Nguyễn Văn An Updated", updated.getFullName());
        assertEquals("0912345679", updated.getPhone());
        assertEquals("an.updated@petcare.vn", updated.getEmail());

        authService.changePassword(1, "Pass@1234", "NewPass@999");
        Users relogin = authService.authenticate("an.updated@petcare.vn", "NewPass@999");
        assertEquals(1, relogin.getUserId());
    }

    @Test
    @DisplayName("TC_REG_DUP_PHONE: Ném ngoại lệ khi số điện thoại đã được đăng ký")
    void testRegisterDuplicatePhoneThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.registerCustomer("Người Dùng Khác", "0912345678", "other@petcare.vn", "Pass@1234"));
        assertEquals("Số điện thoại này đã được đăng ký tài khoản.", ex.getMessage());
    }

    @Test
    @DisplayName("TC_REG_DUP_EMAIL: Ném ngoại lệ khi email đã được sử dụng")
    void testRegisterDuplicateEmailThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.registerCustomer("Người Dùng Khác", "0912999888", "khachhang@petcare.vn", "Pass@1234"));
        assertEquals("Địa chỉ Email này đã được sử dụng.", ex.getMessage());
    }

    @Test
    @DisplayName("TC_AUTH_02: Ném ngoại lệ khi bỏ trống thông tin đăng nhập")
    void testAuthenticateBlankCredentialsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> authService.authenticate("", "Pass@1234"));
        assertThrows(IllegalArgumentException.class, () -> authService.authenticate("0912345678", ""));
        assertThrows(IllegalArgumentException.class, () -> authService.authenticate(null, null));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_AUTH_03: Ném ngoại lệ khi nhập sai mật khẩu")
    void testAuthenticateWrongPasswordThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.authenticate("0912345678", "WrongPass@1"));
        assertEquals("Số điện thoại/Email hoặc mật khẩu không chính xác.", ex.getMessage());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_AUTH_04: Ném ngoại lệ khi tài khoản đang bị khóa")
    void testAuthenticateLockedUserThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.authenticate("0988777666", "Pass@1234"));
        assertTrue(ex.getMessage().contains("bị khóa"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_PROF_02: Đổi mật khẩu thất bại khi sai mật khẩu cũ hoặc mật khẩu mới < 8 ký tự")
    void testChangePasswordExceptions() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(1, "SaiMatKhau", "NewPass@123"));
        assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(1, "Pass@1234", "1234567"));
    }

    @Test
    @DisplayName("TC_HASH_01: AuthService.hashPassword ném ngoại lệ khi mật khẩu rỗng hoặc null")
    void testHashPasswordEmptyOrNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> AuthService.hashPassword(""));
        assertThrows(IllegalArgumentException.class, () -> AuthService.hashPassword(null));
    }

    @ParameterizedTest(name = "[{index}] Biên SĐT=''{0}'' -> hợp lệ={1}")
    @DisplayName("TC_VAL_BOUNDARY: Kiểm tra biên độ dài SĐT (8..12 số) và định dạng ký tự phân tách")
    @CsvSource({
        "'01234567', false",
        "'012345678', true",
        "'0912345678', true",
        "'01234567890', true",
        "'012345678901', false",
        "'(+84) 912.345.678', true"
    })
    void testPhoneLengthBoundaries(String rawPhone, boolean expectedValid) {
        assertEquals(expectedValid, ValidationUtil.isValidPhone(rawPhone));
    }

    @ParameterizedTest(name = "[{index}] Cập nhật hồ sơ không hợp lệ: name=''{0}'', phone=''{1}'', email=''{2}''")
    @DisplayName("TC_PROF_BOUNDARY: Kiểm tra biên form cập nhật hồ sơ ném IllegalArgumentException")
    @CsvSource({
        "'', '0912345678', 'khachhang@petcare.vn', 'Vui lòng nhập họ và tên.'",
        "'Nguyễn Văn An', '09123', 'khachhang@petcare.vn', 'Số điện thoại không hợp lệ'",
        "'Nguyễn Văn An', '0912345678', '', 'Địa chỉ Email không đúng định dạng.'",
        "'Nguyễn Văn An', '0912345678', 'sai-dinh-dang', 'Địa chỉ Email không đúng định dạng.'"
    })
    void testUpdateProfileBoundaries(String fullName, String phone, String email, String expectedFragment) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.updateProfile(1, fullName, phone, email));
        assertTrue(ex.getMessage().contains(expectedFragment),
                "Thông báo lỗi thực tế: " + ex.getMessage());
    }

    @Test
    @DisplayName("TC_PROF_DUP_PHONE: Cập nhật hồ sơ thất bại khi SĐT trùng với tài khoản khác")
    void testUpdateProfileDuplicatePhoneThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.updateProfile(1, "Nguyễn Văn An", "0909000111", "khachhang@petcare.vn"));
        assertEquals("Số điện thoại này đã được sử dụng bởi tài khoản khác.", ex.getMessage());
    }

    @Test
    @DisplayName("TC_PROF_DUP_EMAIL: Cập nhật hồ sơ thất bại khi Email trùng với tài khoản khác")
    void testUpdateProfileDuplicateEmailThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.updateProfile(1, "Nguyễn Văn An", "0912345678", "admin@petcare.vn"));
        assertEquals("Địa chỉ Email này đã được sử dụng bởi tài khoản khác.", ex.getMessage());
    }

    @Test
    @DisplayName("TC_PROF_NOT_FOUND: Cập nhật hồ sơ và đổi mật khẩu khi userId không tồn tại")
    void testProfileAndChangePasswordNotFoundThrowsException() {
        IllegalArgumentException updateEx = assertThrows(IllegalArgumentException.class,
                () -> authService.updateProfile(999, "Không Tồn Tại", "0912345999", "notfound@petcare.vn"));
        assertEquals("Không tìm thấy thông tin tài khoản.", updateEx.getMessage());

        IllegalArgumentException pwEx = assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(999, "Pass@1234", "NewPass@123"));
        assertEquals("Không tìm thấy thông tin tài khoản.", pwEx.getMessage());
    }

    @Test
    @DisplayName("TC_PROF_KEEP_SAME: Cập nhật chỉ họ tên khi giữ nguyên SĐT và Email hiện tại")
    void testUpdateProfileKeepingSamePhoneAndEmailSuccess() throws SQLException {
        Users updated = authService.updateProfile(1, "Nguyễn Văn An Mới", "0912345678", "khachhang@petcare.vn");
        assertEquals("Nguyễn Văn An Mới", updated.getFullName());
        assertEquals("0912345678", updated.getPhone());
        assertEquals("khachhang@petcare.vn", updated.getEmail());
    }

    @Test
    @DisplayName("TC_VERIFY_PW_01: Kiểm tra AuthService.verifyPassword với null/empty và nhánh plaintext")
    void testVerifyPasswordNullEmptyAndPlaintext() {
        assertFalse(AuthService.verifyPassword(null, sharedPasswordHash));
        assertFalse(AuthService.verifyPassword("Pass@1234", null));
        assertFalse(AuthService.verifyPassword("Pass@1234", ""));
        assertTrue(AuthService.verifyPassword("Legacy@123", "Legacy@123"));
        assertFalse(AuthService.verifyPassword("Legacy@123", "WrongLegacy"));
    }

    @Test
    @DisplayName("TC_LOGIN_SERVLET_01: LoginServlet từ chối mật khẩu dưới 6 ký tự và forward /login.jsp")
    void testLoginServletRejectsShortPassword() throws ServletException, IOException {
        LoginServlet servlet = new LoginServlet(authService);
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withParam("identifier", "0912345678")
                .withParam("password", "12345");

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/login.jsp", exchange.getForwardedPath());
        assertEquals("Mật khẩu phải có tối thiểu 6 ký tự.", exchange.getRequestAttribute("errorMessage"));
        assertEquals("0912345678", exchange.getRequestAttribute("identifier"));
        assertFalse(exchange.hasSession());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_LOGIN_SERVLET_02: LoginServlet đăng nhập CUSTOMER với remember='on' đặt session 7 ngày và chuyển /home")
    void testLoginServletCustomerRememberMeRedirectsHome() throws ServletException, IOException {
        LoginServlet servlet = new LoginServlet(authService);
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment")
                .withParam("identifier", "0912345678")
                .withParam("password", "Pass@1234")
                .withParam("remember", "on");

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/sof309-assignment/home", exchange.getRedirectedUrl());
        assertEquals(7 * 24 * 60 * 60, exchange.getMaxInactiveInterval());
        assertEquals(1, exchange.getSessionAttribute(SessionUtil.ATTR_USER_ID));
        assertEquals(Users.ROLE_CUSTOMER, exchange.getSessionAttribute(SessionUtil.ATTR_USER_ROLE));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_LOGIN_SERVLET_03: LoginServlet đăng nhập ADMIN không chọn remember đặt session 30 phút và chuyển /admin/tai-khoan")
    void testLoginServletAdminWithoutRememberRedirectsAdmin() throws ServletException, IOException {
        LoginServlet servlet = new LoginServlet(authService);
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment")
                .withParam("identifier", "admin@petcare.vn")
                .withParam("password", "Pass@1234");

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/sof309-assignment/admin/tai-khoan", exchange.getRedirectedUrl());
        assertEquals(30 * 60, exchange.getMaxInactiveInterval());
        assertEquals(2, exchange.getSessionAttribute(SessionUtil.ATTR_USER_ID));
        assertEquals(Users.ROLE_ADMIN, exchange.getSessionAttribute(SessionUtil.ATTR_USER_ROLE));
    }

    @Test
    @DisplayName("TC_REG_SERVLET_01: RegisterServlet báo lỗi khi mật khẩu xác nhận không khớp")
    void testRegisterServletPasswordMismatchForwardsError() throws ServletException, IOException {
        RegisterServlet servlet = new RegisterServlet(authService);
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withParam("fullName", "Tô Gia Bảo")
                .withParam("phone", "0912345777")
                .withParam("email", "baotg@petcare.vn")
                .withParam("password", "Pass@1234")
                .withParam("confirmPassword", "Pass@9999");

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/register.jsp", exchange.getForwardedPath());
        assertEquals("Mật khẩu nhập lại không khớp với mật khẩu vừa tạo.",
                exchange.getRequestAttribute("errorMessage"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_REG_SERVLET_02: RegisterServlet đăng ký thành công lưu flash session và redirect /login")
    void testRegisterServletSuccessRedirectsLogin() throws ServletException, IOException {
        RegisterServlet servlet = new RegisterServlet(authService);
        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment")
                .withParam("fullName", "Nguyễn Thùy Trang")
                .withParam("phone", "0912345123")
                .withParam("email", "thuytrang@petcare.vn")
                .withParam("password", "Pass@1234")
                .withParam("confirmPassword", "Pass@1234");

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/sof309-assignment/login", exchange.getRedirectedUrl());
        assertEquals("Đăng ký tài khoản thành công!", exchange.getSessionAttribute("successMessage"));
        assertEquals("thuytrang@petcare.vn", exchange.getSessionAttribute("registeredIdentifier"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("TC_PROF_SERVLET_01: ProfileServlet kiểm tra xác nhận mật khẩu lệch và redirect về /home#ho-so-nguoi-dung")
    void testProfileServletConfirmMismatchAndRedirectToHome() throws ServletException, IOException {
        ProfileServlet servlet = new ProfileServlet(authService);
        Users currentUser = fakeAccountDAO.findById(1);

        MockServletHelper.MockExchange mismatchExchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment")
                .withLoggedInUser(currentUser)
                .withParam("action", "changePassword")
                .withParam("currentPassword", "Pass@1234")
                .withParam("newPassword", "NewPass@999")
                .withParam("confirmPassword", "Different@1")
                .withParam("redirectTo", "home");

        servlet.doPost(mismatchExchange.request(), mismatchExchange.response());

        assertEquals("/sof309-assignment/home#ho-so-nguoi-dung", mismatchExchange.getRedirectedUrl());
        assertEquals("Mật khẩu mới và mật khẩu xác nhận không khớp.",
                mismatchExchange.getSessionAttribute("profileError"));

        MockServletHelper.MockExchange updateExchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment")
                .withLoggedInUser(currentUser)
                .withParam("fullName", "Nguyễn Văn An Profile")
                .withParam("phone", "0912345678")
                .withParam("email", "khachhang@petcare.vn")
                .withParam("redirectTo", "home");

        servlet.doPost(updateExchange.request(), updateExchange.response());

        assertEquals("/sof309-assignment/home#ho-so-nguoi-dung", updateExchange.getRedirectedUrl());
        assertEquals("Cập nhật hồ sơ người dùng thành công!",
                updateExchange.getSessionAttribute("profileSuccess"));
        assertEquals("Nguyễn Văn An Profile", fakeAccountDAO.findById(1).getFullName());
    }

    /** In-memory Fake AccountDAO để chạy Unit Test độc lập không cần kết nối SQL Server. */
    private static final class FakeAccountDAO implements AccountDAO {
        private final Map<Integer, Users> storage = new LinkedHashMap<>();
        private int nextId = 10;

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
        public Users create(Users user) {
            Users saved = copy(user);
            saved.setUserId(nextId++);
            if (saved.getCreatedAt() == null) {
                saved.setCreatedAt(LocalDateTime.now());
            }
            storage.put(saved.getUserId(), saved);
            return copy(saved);
        }

        @Override
        public boolean updateRoleAndStatus(int userId, String role, boolean isActive) {
            Users target = storage.get(userId);
            if (target == null) {
                return false;
            }
            target.setRole(role);
            target.setActive(isActive);
            return true;
        }

        @Override
        public boolean updateProfile(Users user) {
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
        public boolean updatePassword(int userId, String passwordHash) {
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
