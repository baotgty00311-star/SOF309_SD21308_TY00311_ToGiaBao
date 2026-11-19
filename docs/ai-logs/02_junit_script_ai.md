# Nhật ký AI: Dùng AI hỗ trợ viết Unit Test bằng JUnit 5 (Y3)

## 1. Ngữ cảnh & Mục tiêu
- **Giai đoạn:** Tuần 2 — Yêu cầu **Y3** (Viết Unit Test cho Controller/Service trong `src/test/java/com/poly/sof309/controllers/`).
- **Đối tượng kiểm thử của dự án PetCare:**
  - `AuthService.java` & `ValidationUtil.java` (đăng ký, đăng nhập, cập nhật hồ sơ, đổi mật khẩu, băm BCrypt, chuẩn hóa/kiểm tra SĐT và Email).
  - `BookingService.java`, `HomeServlet.java` & `AppointmentBalance.java` (đặt lịch dịch vụ, tính thời gian kết thúc `scheduledEnd`, kiểm tra chủ sở hữu thú cưng/trạng thái dịch vụ, ánh xạ mã lỗi SQL Server, dữ liệu mặc định trang chủ và tính số dư thanh toán).
- **Yêu cầu kỹ thuật:** Áp dụng JUnit 5 nâng cao gồm `@ParameterizedTest` + `@CsvSource`, `assertThrows()`, `@Timeout`, và chạy 100% pass bằng `mvn test` mà không phụ thuộc vào SQL Server đang chạy ở local hay GitHub Actions.

## 2. Câu lệnh đã gửi cho AI (Prompt)
> "Dựa trên `AuthService.java`, `BookingService.java`, `ValidationUtil.java`, `HomeServlet.java`, `AppointmentBalance.java` và bộ test case trong `docs/TEST_CASES.md`, hãy viết 2 class Unit Test bằng JUnit 5 trong package `com.poly.sof309.controllers`:
> 1. `AuthControllerTest.java`: dùng `@ParameterizedTest` + `@CsvSource` kiểm thử biên SĐT, Email, Form đăng ký; dùng `@Test` + `assertThrows()` + `@Timeout` kiểm thử đăng ký, đăng nhập, khóa tài khoản, đổi mật khẩu.
> 2. `BookingControllerTest.java`: kiểm thử đặt lịch thành công, ngoại lệ thú cưng/dịch vụ không hợp lệ, ánh xạ mã lỗi SQL Server (`51001..51032`), `HomeServlet` và `AppointmentBalance`.
> Yêu cầu test chạy độc lập bằng `mvn test` không cần kết nối SQL Server."

## 3. Kết quả do AI sinh ra (Raw Output)
```java
// Đoạn code gốc AI đề xuất ban đầu (trước khi sinh viên chỉnh sửa):
public class AuthControllerTest {
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // AI dùng constructor mặc định khiến test gọi thẳng SQL Server thật
        authService = new AuthService();
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void testRegisterCustomer() throws SQLException {
        // AI truyền email "" và kỳ vọng ném IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> authService.registerCustomer("Bao", "0912345678", "", "12345678"));
    }

    @Test
    void testAuthenticateSuccess() throws SQLException {
        // AI sinh BCrypt hash trong mỗi @BeforeEach làm chạy chậm
        String hash = AuthService.hashPassword("Pass@1234");
        assertNotNull(authService.authenticate("0912345678", "Pass@1234"));
    }
}

public class BookingControllerTest {
    @Test
    void testBookService() throws SQLException {
        // AI gọi new BookingService(mockPetDao, mockServiceDao, mockAppointmentDao)
        // nhưng BookingService.java lúc đầu chưa có constructor nhận 3 DAO!
        BookingService service = new BookingService();
        int id = service.bookService(1, 1, 1, 1, LocalDateTime.now());
        assertTrue(id > 0);
    }
}
```

## 4. Chỉnh sửa của Sinh viên & Lý do sửa
- **Những điểm AI làm sai hoặc thiếu:**
  1. **Thiếu Dependency Injection trong `BookingService` và lệ thuộc SQL Server thật:** `BookingService.java` ban đầu chỉ có constructor mặc định `new BookingService()` tự khởi tạo `PetDAOImpl`, `ServiceDAOImpl`, `AppointmentDAOImpl` kết nối JDBC SQL Server. Ở môi trường test/CI không có SQL Server thì test sẽ fail.
     - *Cách sửa:* Giữ nguyên constructor mặc định `public BookingService()` để Web App chạy bình thường và bổ sung constructor `public BookingService(PetDAO petDAO, ServiceDAO serviceDAO, AppointmentDAO appointmentDAO)` trong `src/main/java/com/petcare/services/BookingService.java` (tương tự pattern đã có ở `AuthService(AccountDAO)`). Đồng thời viết các lớp `FakeAccountDAO`, `FakePetDAO`, `FakeServiceDAO`, `FakeAppointmentDAO` chạy in-memory trong file test (không cần thêm thư viện Mockito vào `pom.xml`).
  2. **Hiểu sai nghiệp vụ bỏ trống Email khi đăng ký:** AI viết test mong đợi `registerCustomer(..., "", ...)` ném lỗi. Thực tế `AuthService.registerCustomer()` tự động sinh `<cleanPhone>@petcare.vn` khi email rỗng.
     - *Cách sửa:* Sửa thành ca kiểm thử `TC_REG_02` kiểm tra email tự sinh đúng định dạng `0912345888@petcare.vn`, đồng thời dùng `@ParameterizedTest` (`TC_REG_BOUNDARY`) để kiểm tra định dạng email sai như `"email-sai"`.
  3. **Tối ưu hiệu năng BCrypt (`cost = 12`) và `@Timeout`:** Nếu tạo `BCrypt.hashToString(12, ...)` cho 3 tài khoản mẫu trong `@BeforeEach` trước mỗi test method thì 24 test case sẽ mất rất nhiều thời gian và dễ chạm ngưỡng `@Timeout(1s)`.
     - *Cách sửa:* Tính trước `sharedPasswordHash` một lần duy nhất trong `@BeforeAll` và đặt `@Timeout(value = 5, unit = TimeUnit.SECONDS)` cho các test có băm/kiểm tra BCrypt.
  4. **Bổ sung kiểm thử ánh xạ mã lỗi SQL & các hàm helper chưa được AI viết:** Thêm `@ParameterizedTest` + `@CsvSource` (`TC_BOOK_SQL_MAP`) cho đủ 6 mã lỗi trong `BookingService.FRIENDLY_ERRORS` (`51001`, `51031`, `51032`, `51009`, `51010`, `51008`) cộng mã lỗi mặc định `99999`, và thêm unit test cho `HomeServlet.buildDefaultServices()`, `HomeServlet.buildDefaultReviews()`, `AppointmentBalance.getRemainingAmount()`.

- **Code hoàn thiện & Kết quả chạy kiểm chứng (`mvn test`):**
  - Đã hoàn thiện `src/test/java/com/poly/sof309/controllers/AuthControllerTest.java` (24 ca kiểm thử) và `src/test/java/com/poly/sof309/controllers/BookingControllerTest.java` (14 ca kiểm thử).
  - Kết quả thực thi `mvn test`:
    - `AuthControllerTest`: `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`
    - `BookingControllerTest`: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`
    - `SampleControllerTest`: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
    - `LoginTest` (Selenium UI): `Skipped: 1` (`@Disabled`)
    - Tổng: **41 tests, 0 Failures, 0 Errors, BUILD SUCCESS**.
